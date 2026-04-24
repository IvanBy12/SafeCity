package com.example.safecity.repository

import android.util.Log
import com.example.safecity.models.Comment
import com.example.safecity.models.Incident
import com.example.safecity.models.IncidentType
import com.example.safecity.network.*
import com.example.safecity.store.UserPreferencesStore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.GeoPoint
import com.google.gson.JsonElement
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Headers
import okhttp3.RequestBody
import okio.Buffer
import retrofit2.HttpException
import java.util.Locale

class IncidentRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    private val api = ApiClient.api
    private val TAG = "IncidentRepository"

    init {
        ensurePollingStarted()
    }

    companion object {
        private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        private val incidentsState = MutableStateFlow<List<Incident>>(emptyList())
        private val dataChanges = MutableSharedFlow<Long>(replay = 1, extraBufferCapacity = 32)

        @Volatile
        private var pollingStarted = false
        private var pollingJob: Job? = null

        private const val POLLING_INTERVAL_MS = 4_000L
        private const val POLLING_RETRY_MS = 12_000L
        private val VALID_MY_REPORT_STATUSES = setOf("pending", "verified", "closed")
        private val recentUpserts = mutableMapOf<String, Long>()
        private val recentDeletes = mutableMapOf<String, Long>()
        private val mutationGuardLock = Any()
        private const val LOCAL_MUTATION_GUARD_MS = 12_000L
    }

    // ==========================================
    // LISTAR INCIDENTES REACTIVO + POLLING GLOBAL
    // ==========================================
    fun getIncidentsFlow(): Flow<List<Incident>> {
        ensurePollingStarted()
        if (incidentsState.value.isEmpty()) {
            repositoryScope.launch {
                refreshIncidentsNow()
            }
        }
        return incidentsState.asStateFlow()
    }

    fun observeDataChanges(): Flow<Long> = dataChanges.asSharedFlow()

    suspend fun refreshIncidentsNow(): Result<List<Incident>> = fetchAndPublishIncidents(forceNotify = true)

    private fun ensurePollingStarted() {
        if (pollingStarted) return

        synchronized(Companion) {
            if (pollingStarted) return

            pollingStarted = true
            pollingJob = repositoryScope.launch {
                while (isActive) {
                    val result = fetchAndPublishIncidents(forceNotify = false)
                    delay(if (result.isSuccess) POLLING_INTERVAL_MS else POLLING_RETRY_MS)
                }
            }
        }
    }

    private suspend fun fetchAndPublishIncidents(forceNotify: Boolean): Result<List<Incident>> {
        return try {
            val token = TokenStore.getOrRefresh()
            if (token.isNullOrBlank()) {
                if (incidentsState.value.isNotEmpty()) {
                    publishIncidents(emptyList(), forceNotify = true)
                }
                return Result.failure(Exception("No autenticado"))
            }

            val response = api.listIncidents("Bearer $token")
            val mapped = response.data.map { it.toIncident() }
            val guarded = applyRecentMutationGuard(mapped)
            publishIncidents(guarded, forceNotify)
            Result.success(guarded)
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            val details = buildHttpErrorDetails(flow = "refreshIncidentsNow", error = e)
            Log.e(TAG, details)
            Result.failure(Exception(details, e))
        } catch (e: Exception) {
            Log.e(TAG, "Error cargando incidentes: ${e.message}")
            Result.failure(e)
        }
    }

    private fun publishIncidents(newList: List<Incident>, forceNotify: Boolean = false) {
        val changed = newList != incidentsState.value
        if (changed) {
            incidentsState.value = newList
        }
        if (changed || forceNotify) {
            dataChanges.tryEmit(System.currentTimeMillis())
        }
    }

    private fun mutateIncidents(forceNotify: Boolean = true, transform: (List<Incident>) -> List<Incident>) {
        val previous = incidentsState.value
        val updated = transform(previous)
        trackRecentMutations(previous, updated)
        publishIncidents(updated, forceNotify = forceNotify)
    }

    private fun scheduleConsistencyRefresh() {
        repositoryScope.launch {
            refreshIncidentsNow()
        }
    }

    // ==========================================
    // CONSULTA PUNTUAL PARA REPORTES
    // ==========================================
    suspend fun getIncidentsOnce(): Result<List<Incident>> {
        val refresh = refreshIncidentsNow()
        if (refresh.isSuccess) return refresh

        return if (incidentsState.value.isNotEmpty()) {
            Result.success(incidentsState.value)
        } else {
            refresh
        }
    }

    // ==========================================
    // MIS REPORTES (REQ 2)
    // ==========================================
    suspend fun getMyIncidents(status: String? = null): Result<List<Incident>> {
        val normalizedStatus = normalizeMyReportStatus(status)

        return try {
            val token = TokenStore.getOrRefresh()
                ?: return Result.failure(Exception("No autenticado"))
            val response = api.getMyIncidents("Bearer $token", status = normalizedStatus)
            Result.success(response.data.map { it.toIncident() })
        } catch (e: HttpException) {
            val details = buildHttpErrorDetails(flow = "getMyIncidents", error = e)
            if (e.code() == 400) {
                Log.w(TAG, "$details | fallback=uid-filter")
                fallbackGetMyIncidents(normalizedStatus)
            } else {
                Log.e(TAG, details)
                Result.failure(Exception(details, e))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cargando mis reportes: ${e.message}")
            Result.failure(e)
        }
    }

    /** Fallback: obtiene todos los incidentes y filtra por UID del usuario autenticado */
    private suspend fun fallbackGetMyIncidents(status: String?): Result<List<Incident>> {
        return try {
            val token = TokenStore.getOrRefresh()
                ?: return Result.failure(Exception("No autenticado"))
            val currentUserId = auth.currentUser?.uid
                ?: return Result.failure(Exception("No autenticado"))
            val allIncidents = api.listIncidents("Bearer $token").data.map { it.toIncident() }
            val mine = allIncidents
                .filter { it.userId == currentUserId }
                .let { list -> if (status != null) list.filter { it.status == status } else list }
            Result.success(mine)
        } catch (e: Exception) {
            Log.e(TAG, "Error en fallback getMyIncidents: ${e.message}")
            Result.failure(e)
        }
    }

    // ==========================================
    // EDITAR INCIDENTE DENTRO DEL PLAZO (REQ 2)
    // ==========================================
    suspend fun editIncident(incidentId: String, description: String): Result<Unit> {
        return try {
            val token = TokenStore.getOrRefresh()
                ?: return Result.failure(Exception("No autenticado"))
            val resp = api.editIncident(
                auth = "Bearer $token",
                id = incidentId,
                request = EditIncidentReq(description = description)
            )
            if (resp.success) {
                val detail = resp.data
                val currentUserId = auth.currentUser?.uid ?: ""
                mutateIncidents { current ->
                    current.map { incident ->
                        if (incident.id != incidentId) incident
                        else {
                            val votedTrueList = detail.votedTrue ?: incident.votedTrue
                            val votedFalseList = detail.votedFalse ?: incident.votedFalse
                            incident.copy(
                                type = when (detail.categoryGroup.uppercase()) {
                                    "INFRAESTRUCTURA" -> IncidentType.INFRAESTRUCTURA
                                    else -> IncidentType.SEGURIDAD
                                },
                                category = detail.type,
                                description = detail.description,
                                address = detail.address ?: incident.address,
                                status = detail.status,
                                verified = detail.verified ?: incident.verified,
                                flaggedFalse = detail.flaggedFalse ?: incident.flaggedFalse,
                                validationScore = detail.validationScore ?: incident.validationScore,
                                votedTrue = votedTrueList,
                                votedFalse = votedFalseList,
                                votedTrueCount = votedTrueList.size,
                                votedFalseCount = votedFalseList.size,
                                commentsCount = detail.commentsCount ?: incident.commentsCount,
                                userVoteStatus = when {
                                    votedTrueList.contains(currentUserId) -> "true"
                                    votedFalseList.contains(currentUserId) -> "false"
                                    else -> "none"
                                }
                            )
                        }
                    }
                }
                scheduleConsistencyRefresh()
                Result.success(Unit)
            } else {
                Result.failure(Exception(resp.error ?: "Error al editar"))
            }
        } catch (e: HttpException) {
            val details = buildHttpErrorDetails(flow = "editIncident", error = e)
            Log.e(TAG, details)
            Result.failure(Exception(details, e))
        } catch (e: Exception) {
            Log.e(TAG, "Error editando incidente: ${e.message}")
            Result.failure(e)
        }
    }

    // ==========================================
    // ESTADÍSTICAS AGREGADAS (REQ 3)
    // ==========================================
    suspend fun getAggregateStats(year: Int? = null, month: Int? = null): Result<AggregateStatsResponse> {
        val (safeYear, safeMonth) = normalizeStatsPeriod(year = year, month = month)

        return try {
            val token = TokenStore.getOrRefresh()
                ?: return Result.failure(Exception("No autenticado"))
            val resp = api.getAggregateStats("Bearer $token", year = safeYear, month = safeMonth)
            Result.success(resp)
        } catch (e: HttpException) {
            val details = buildHttpErrorDetails(flow = "getAggregateStats", error = e)
            Log.e(TAG, details)
            Result.failure(Exception(details, e))
        } catch (e: Exception) {
            Log.e(TAG, "Error cargando estadísticas: ${e.message}")
            Result.failure(e)
        }
    }

    // ==========================================
    // BUSCAR CERCANOS
    // ==========================================
    suspend fun getNearbyIncidents(lat: Double, lng: Double, radiusKm: Int = 5): Result<List<Incident>> {
        return try {
            val token = TokenStore.getOrRefresh()
                ?: return Result.failure(Exception("No autenticado"))
            val response = api.listNearby("Bearer $token", lat, lng, radiusKm)
            Result.success(response.data.map { it.toIncident() })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==========================================
    // COMENTARIOS
    // ==========================================
    suspend fun getIncidentComments(incidentId: String): Result<List<Comment>> {
        return try {
            val token = TokenStore.getOrRefresh()
                ?: return Result.failure(Exception("No autenticado"))
            val resp = api.getIncidentDetail("Bearer $token", incidentId)
            if (!resp.success) return Result.failure(Exception("No se pudo cargar el incidente"))
            val comments = (resp.data.comments ?: emptyList()).map { c ->
                Comment(
                    id = c._id,
                    text = c.text,
                    authorUid = c.authorUid,
                    isAnonymous = c.isAnonymous ?: false,
                    createdAt = parseTimestamp(c.createdAt)
                )
            }
            Result.success(comments)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==========================================
    // CREAR INCIDENTE — REQ 1: pasa isAnonymous
    // ==========================================
    suspend fun createIncident(incident: Incident, photoUrls: List<String> = emptyList()): Result<String> {
        return try {
            val token = TokenStore.getOrRefresh()
                ?: return Result.failure(Exception("No se pudo obtener token"))
            val allPhotos = photoUrls.ifEmpty { incident.photos }
            val request = CreateIncidentReq(
                categoryGroup = incident.type.name,
                type = incident.category,
                title = incident.category,
                description = incident.description,
                latitude = incident.location.latitude,
                longitude = incident.location.longitude,
                address = incident.address,
                photos = allPhotos,
                isAnonymous = incident.isAnonymous
            )
            val wrapper = api.createIncident("Bearer $token", request)
            val createdIncident = wrapper.data.toIncident()
            mutateIncidents { current ->
                listOf(createdIncident) + current.filterNot { it.id == createdIncident.id }
            }
            scheduleConsistencyRefresh()
            Result.success(wrapper.data._id)
        } catch (e: HttpException) {
            val details = buildHttpErrorDetails(flow = "createIncident", error = e)
            Log.e(TAG, details)
            Result.failure(Exception(details, e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==========================================
    // VOTOS
    // ==========================================
    suspend fun voteTrue(incidentId: String): Result<Unit> = runVote(incidentId, "true") {
        api.voteTrue("Bearer $it", incidentId)
    }

    suspend fun voteFalse(incidentId: String): Result<Unit> = runVote(incidentId, "false") {
        api.voteFalse("Bearer $it", incidentId)
    }

    suspend fun removeVote(incidentId: String): Result<Unit> = runVote(incidentId, "none") {
        api.removeVote("Bearer $it", incidentId)
    }

    suspend fun confirmIncident(incidentId: String) = voteTrue(incidentId)
    suspend fun unconfirmIncident(incidentId: String) = removeVote(incidentId)

    private suspend fun runVote(
        incidentId: String,
        voteStatusAfterSuccess: String,
        block: suspend (String) -> VoteResponse
    ): Result<Unit> {
        return try {
            val token = TokenStore.getOrRefresh() ?: return Result.failure(Exception("No autenticado"))
            val resp = block(token)
            if (resp.success) {
                val payload = resp.data
                if (payload != null) {
                    mutateIncidents { current ->
                        current.map { incident ->
                            if (incident.id != incidentId) incident
                            else incident.copy(
                                validationScore = payload.validationScore,
                                votedTrueCount = payload.votedTrue,
                                votedFalseCount = payload.votedFalse,
                                verified = payload.verified,
                                flaggedFalse = payload.flaggedFalse,
                                status = payload.status,
                                userVoteStatus = voteStatusAfterSuccess
                            )
                        }
                    }
                } else {
                    dataChanges.tryEmit(System.currentTimeMillis())
                }
                scheduleConsistencyRefresh()
                Result.success(Unit)
            } else {
                Result.failure(Exception(resp.error ?: "Error en votación"))
            }
        } catch (e: HttpException) {
            val details = buildHttpErrorDetails(flow = "runVote", error = e)
            Log.e(TAG, details)
            Result.failure(Exception(details, e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==========================================
    // COMENTARIO — REQ 1: pasa isAnonymous
    // ==========================================
    suspend fun addComment(incidentId: String, text: String): Result<Unit> {
        return try {
            val token = TokenStore.getOrRefresh() ?: return Result.failure(Exception("No autenticado"))
            val anonymous = UserPreferencesStore.defaultAnonymous
            val resp = api.addComment(
                auth = "Bearer $token",
                id = incidentId,
                comment = CommentRequest(text = text, isAnonymous = anonymous)
            )
            if (!resp.success) return Result.failure(Exception(resp.error ?: "Error enviando comentario"))
            mutateIncidents { current ->
                current.map { incident ->
                    if (incident.id == incidentId) {
                        incident.copy(commentsCount = incident.commentsCount + 1)
                    } else incident
                }
            }
            scheduleConsistencyRefresh()
            Result.success(Unit)
        } catch (e: HttpException) {
            val details = buildHttpErrorDetails(flow = "addComment", error = e)
            Log.e(TAG, details)
            Result.failure(Exception(details, e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==========================================
    // ELIMINAR
    // ==========================================
    suspend fun deleteIncident(incidentId: String): Result<Unit> {
        return try {
            val token = TokenStore.getOrRefresh() ?: return Result.failure(Exception("No autenticado"))
            api.deleteIncident("Bearer $token", incidentId)
            mutateIncidents { current ->
                current.filterNot { it.id == incidentId }
            }
            scheduleConsistencyRefresh()
            Result.success(Unit)
        } catch (e: HttpException) {
            val details = buildHttpErrorDetails(flow = "deleteIncident", error = e)
            Log.e(TAG, details)
            Result.failure(Exception(details, e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==========================================
    // HELPERS
    // ==========================================
    private fun trackRecentMutations(previous: List<Incident>, updated: List<Incident>) {
        val now = System.currentTimeMillis()
        val previousById = previous.associateBy { it.id }
        val updatedById = updated.associateBy { it.id }

        synchronized(mutationGuardLock) {
            pruneRecentMutationsLocked(now)

            for ((id, updatedIncident) in updatedById) {
                val previousIncident = previousById[id]
                if (previousIncident != updatedIncident) {
                    recentUpserts[id] = now
                    recentDeletes.remove(id)
                }
            }

            for (deletedId in previousById.keys - updatedById.keys) {
                recentDeletes[deletedId] = now
                recentUpserts.remove(deletedId)
            }
        }
    }

    private fun applyRecentMutationGuard(fetched: List<Incident>): List<Incident> {
        val now = System.currentTimeMillis()
        val localById = incidentsState.value.associateBy { it.id }

        synchronized(mutationGuardLock) {
            pruneRecentMutationsLocked(now)

            for (incident in fetched) {
                val id = incident.id
                val local = localById[id]
                if (local != null && recentUpserts.containsKey(id) && local == incident) {
                    recentUpserts.remove(id)
                }
            }

            val fetchedIds = fetched.map { it.id }.toHashSet()
            for (deletedId in recentDeletes.keys.toList()) {
                if (deletedId !in fetchedIds) {
                    recentDeletes.remove(deletedId)
                }
            }

            val guarded = fetched
                .asSequence()
                .filterNot { incident -> recentDeletes.containsKey(incident.id) }
                .map { incident ->
                    if (recentUpserts.containsKey(incident.id)) {
                        localById[incident.id] ?: incident
                    } else {
                        incident
                    }
                }
                .toMutableList()

            val guardedIds = guarded.map { it.id }.toHashSet()
            val pendingLocalOnly = recentUpserts.keys
                .asSequence()
                .filterNot { it in guardedIds }
                .mapNotNull { localById[it] }
                .toList()

            if (pendingLocalOnly.isNotEmpty()) {
                guarded.addAll(0, pendingLocalOnly)
            }

            return guarded
        }
    }

    private fun pruneRecentMutationsLocked(now: Long) {
        recentUpserts.entries.removeAll { now - it.value > LOCAL_MUTATION_GUARD_MS }
        recentDeletes.entries.removeAll { now - it.value > LOCAL_MUTATION_GUARD_MS }
    }
    private fun normalizeMyReportStatus(status: String?): String? {
        val normalized = status?.trim()?.lowercase(Locale.ROOT) ?: return null
        return normalized.takeIf { it in VALID_MY_REPORT_STATUSES }
    }

    private fun normalizeStatsPeriod(year: Int?, month: Int?): Pair<Int?, Int?> {
        if (year == null && month == null) {
            return null to null
        }

        if (year != null && month != null && month in 1..12) {
            return year to month
        }

        Log.w(TAG, "Periodo invalido para estadisticas. year=$year month=$month. Se enviara sin filtros")
        return null to null
    }

    private fun buildHttpErrorDetails(flow: String, error: HttpException): String {
        val response = error.response()
        val request = response?.raw()?.request
        val url = request?.url

        val query = url?.queryParameterNames
            ?.associateWith { key -> url.queryParameterValues(key).joinToString(",") }
            ?: emptyMap()

        val headers = request?.headers?.let { sanitizeHeaders(it) } ?: emptyMap()
        val requestBody = requestBodyToText(request?.body)
        val backendBody = try {
            response?.errorBody()?.string().orEmpty()
        } catch (_: Exception) {
            ""
        }

        return "[$flow] HTTP ${error.code()} ${request?.method ?: "UNKNOWN"} ${url?.encodedPath ?: "unknown"} | query=$query | headers=$headers | body=${requestBody.take(500)} | backendError=${backendBody.ifBlank { "<empty>" }.take(700)}"
    }

    private fun sanitizeHeaders(headers: Headers): Map<String, String> {
        return headers.names().sorted().associateWith { name ->
            val raw = headers.values(name).joinToString(",")
            if (name.equals("Authorization", ignoreCase = true)) {
                if (raw.startsWith("Bearer ", ignoreCase = true)) "Bearer ***" else "***"
            } else {
                raw
            }
        }
    }

    private fun requestBodyToText(body: RequestBody?): String {
        if (body == null) return "<empty>"
        return try {
            val buffer = Buffer()
            body.writeTo(buffer)
            buffer.readUtf8().ifBlank { "<empty>" }
        } catch (_: Exception) {
            "<unavailable>"
        }
    }
    private fun parsePhotos(photosElement: JsonElement?): List<String> {
        if (photosElement == null || photosElement.isJsonNull) return emptyList()
        if (!photosElement.isJsonArray) return emptyList()
        val result = mutableListOf<String>()
        for (element in photosElement.asJsonArray) {
            try {
                when {
                    element.isJsonPrimitive && element.asJsonPrimitive.isString -> {
                        val url = element.asString
                        if (url.isNotBlank()) result.add(url)
                    }

                    element.isJsonObject -> {
                        val obj = element.asJsonObject
                        val url = obj.get("url")?.asString
                            ?: obj.get("imageUrl")?.asString
                            ?: obj.get("downloadUrl")?.asString
                            ?: obj.get("uri")?.asString
                        if (!url.isNullOrBlank()) result.add(url)
                    }
                }
            } catch (_: Exception) {
            }
        }
        return result
    }

    private fun IncidentResp.toIncident(): Incident {
        val lng = location.coordinates.getOrNull(0) ?: 0.0
        val lat = location.coordinates.getOrNull(1) ?: 0.0
        val currentUserId = auth.currentUser?.uid ?: ""

        val votedTrueList = votedTrue ?: emptyList()
        val votedFalseList = votedFalse ?: emptyList()
        val score = validationScore ?: (votedTrueList.size - votedFalseList.size)
        val photosList = parsePhotos(photos)

        val userVote = when {
            votedTrueList.contains(currentUserId) -> "true"
            votedFalseList.contains(currentUserId) -> "false"
            else -> "none"
        }

        return Incident(
            id = _id,
            type = when (categoryGroup.uppercase()) {
                "INFRAESTRUCTURA" -> IncidentType.INFRAESTRUCTURA
                else -> IncidentType.SEGURIDAD
            },
            category = type,
            description = description,
            location = GeoPoint(lat, lng),
            address = address ?: "",
            imageUrl = photosList.firstOrNull(),
            photos = photosList,
            userId = reporterUid ?: "",
            userName = if (isAnonymous) "Anónimo" else "Usuario",
            isAnonymous = isAnonymous,
            timestamp = parseTimestamp(createdAt),
            validationScore = score,
            votedTrueCount = votedTrueList.size,
            votedFalseCount = votedFalseList.size,
            verified = verified ?: (score >= 3),
            flaggedFalse = flaggedFalse ?: (score <= -5),
            userVoteStatus = userVote,
            commentsCount = commentsCount ?: 0,
            confirmations = confirmationsCount,
            confirmedBy = confirmedBy ?: emptyList(),
            votedTrue = votedTrueList,
            votedFalse = votedFalseList,
            status = status,
            isEditable = isEditable ?: false,
            editSecondsLeft = editSecondsLeft ?: 0,
            daysUntilClose = daysUntilClose ?: 0
        )
    }

    private fun parseTimestamp(iso: String): Long = try {
        java.time.Instant.parse(iso).toEpochMilli()
    } catch (_: Exception) {
        System.currentTimeMillis()
    }
}

