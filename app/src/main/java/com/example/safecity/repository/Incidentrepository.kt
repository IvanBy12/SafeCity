package com.example.safecity.repository

import android.util.Log
import com.example.safecity.models.Comment
import com.example.safecity.models.Incident
import com.example.safecity.models.IncidentType
import com.example.safecity.network.ApiClient
import com.example.safecity.network.CommentRequest
import com.example.safecity.network.CreateIncidentReq
import com.example.safecity.network.IncidentResp
import com.example.safecity.network.TokenStore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.GeoPoint
import com.google.gson.JsonElement
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

class IncidentRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    private val api = ApiClient.api
    private val TAG = "IncidentRepository"

    // ==========================================
    // LISTAR INCIDENTES CON POLLING
    // ==========================================
    fun getIncidentsFlow(): Flow<List<Incident>> = flow {
        while (currentCoroutineContext().isActive) {
            try {
                // getOrRefresh() retorna el token cacheado si es válido,
                // o hace un refresh forzado si está expirado o es null.
                val token = TokenStore.getOrRefresh()

                if (token.isNullOrBlank()) {
                    Log.e(TAG, "Sin token disponible, reintentando en 10s")
                    emit(emptyList())
                    delay(10_000)
                    continue
                }

                val response = api.listIncidents("Bearer $token")
                val incidents = response.data.map { it.toIncident() }
                Log.d(TAG, "Incidentes recibidos: ${incidents.size}")
                emit(incidents)

                delay(10_000)

            } catch (e: CancellationException) {
                Log.d(TAG, "Polling cancelado")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error cargando incidentes: ${e.message}")
                emit(emptyList())
                delay(30_000)
            }
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
            Log.e(TAG, "Error buscando cercanos: ${e.message}")
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
            Log.e(TAG, "Error cargando comentarios: ${e.message}")
            Result.failure(e)
        }
    }

    // ==========================================
    // CREAR INCIDENTE
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
                photos = allPhotos
            )
            val response = api.createIncident("Bearer $token", request)
            Result.success(response._id)
        } catch (e: Exception) {
            Log.e(TAG, "Error creando incidente: ${e.message}")
            Result.failure(e)
        }
    }

    // ==========================================
    // VOTACIÓN
    // ==========================================
    suspend fun voteTrue(incidentId: String): Result<Unit> {
        return try {
            val token = TokenStore.getOrRefresh() ?: return Result.failure(Exception("No autenticado"))
            val response = api.voteTrue("Bearer $token", incidentId)
            if (response.success) Result.success(Unit)
            else Result.failure(Exception(response.error ?: "Error votando"))
        } catch (e: Exception) {
            Log.e(TAG, "Error votando verdadero: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun voteFalse(incidentId: String): Result<Unit> {
        return try {
            val token = TokenStore.getOrRefresh() ?: return Result.failure(Exception("No autenticado"))
            val response = api.voteFalse("Bearer $token", incidentId)
            if (response.success) Result.success(Unit)
            else Result.failure(Exception(response.error ?: "Error votando"))
        } catch (e: Exception) {
            Log.e(TAG, "Error votando falso: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun removeVote(incidentId: String): Result<Unit> {
        return try {
            val token = TokenStore.getOrRefresh() ?: return Result.failure(Exception("No autenticado"))
            val response = api.removeVote("Bearer $token", incidentId)
            if (response.success) Result.success(Unit)
            else Result.failure(Exception(response.error ?: "Error removiendo voto"))
        } catch (e: Exception) {
            Log.e(TAG, "Error removiendo voto: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun confirmIncident(incidentId: String): Result<Unit> = voteTrue(incidentId)
    suspend fun unconfirmIncident(incidentId: String): Result<Unit> = removeVote(incidentId)

    // ==========================================
    // COMENTARIO
    // ==========================================
    suspend fun addComment(incidentId: String, text: String): Result<Unit> {
        return try {
            val token = TokenStore.getOrRefresh() ?: return Result.failure(Exception("No autenticado"))
            val resp = api.addComment("Bearer $token", incidentId, CommentRequest(text))
            if (!resp.success) return Result.failure(Exception(resp.error ?: "Error enviando comentario"))
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error comentando: ${e.message}")
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
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error eliminando: ${e.message}")
            Result.failure(e)
        }
    }

    // ==========================================
    // HELPERS
    // ==========================================
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
                            ?: obj.get("src")?.asString
                        if (!url.isNullOrBlank()) result.add(url)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error parseando photo: ${e.message}")
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
            userId = reporterUid,
            userName = "Usuario",
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
            votedFalse = votedFalseList
        )
    }

    private fun parseTimestamp(isoString: String): Long {
        return try {
            java.time.Instant.parse(isoString).toEpochMilli()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }
}