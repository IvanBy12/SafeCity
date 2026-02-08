package com.example.safecity.repository

import android.util.Log
import com.example.safecity.models.Incident
import com.example.safecity.models.IncidentType
import com.example.safecity.network.ApiClient
import com.example.safecity.network.CommentRequest
import com.example.safecity.network.CreateIncidentReq
import com.example.safecity.network.IncidentResp
import com.example.safecity.network.TokenStore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.GeoPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class IncidentRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    private val api = ApiClient.api
    private val TAG = "IncidentRepository"

    // ==========================================
    // LISTAR INCIDENTES CON POLLING AUTOMÁTICO
    // ==========================================

    fun getIncidentsFlow(): Flow<List<Incident>> = flow {
        while (true) {  // ✅ Loop infinito para polling
            try {
                Log.d(TAG, "🔄 Actualizando incidentes...")

                val token = TokenStore.get() ?: run {
                    Log.d(TAG, "⚠️ No hay token en cache, refrescando...")
                    TokenStore.refresh()
                    TokenStore.get()
                }

                if (token.isNullOrBlank()) {
                    Log.e(TAG, "❌ Token vacío después de refresh")
                    emit(emptyList())
                    delay(30000) // Esperar 30s antes de reintentar
                    continue
                }

                val paginatedResponse = api.listIncidents("Bearer $token")
                val response = paginatedResponse.data

                Log.d(TAG, "✅ Response recibida: ${response.size} incidentes")

                val incidents = response.map { it.toIncident() }

                emit(incidents)

                // ✅ Esperar 10 segundos antes de la próxima actualización
                delay(10000)

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error cargando incidentes: ${e.message}", e)
                emit(emptyList())
                delay(30000) // En caso de error, esperar 30s
            }
        }
    }

    // ==========================================
    // BUSCAR CERCANOS
    // ==========================================

    suspend fun getNearbyIncidents(
        lat: Double,
        lng: Double,
        radiusKm: Int = 5
    ): Result<List<Incident>> {
        return try {
            Log.d(TAG, "🔍 Buscando incidentes cercanos: lat=$lat, lng=$lng, radius=$radiusKm")

            val token = getToken() ?: return Result.failure(Exception("No autenticado"))

            val paginatedResponse = api.listNearby("Bearer $token", lat, lng, radiusKm)
            val response = paginatedResponse.data

            Log.d(TAG, "✅ Cercanos encontrados: ${response.size}")

            val incidents = response.map { it.toIncident() }
            Result.success(incidents)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error buscando cercanos: ${e.message}", e)
            Result.failure(e)
        }
    }

    // ==========================================
    // CREAR INCIDENTE
    // ==========================================

    suspend fun createIncident(incident: Incident): Result<String> {
        return try {
            val user = auth.currentUser ?: return Result.failure(Exception("No autenticado"))
            val token = getToken() ?: return Result.failure(Exception("No se pudo obtener token"))

            Log.d(TAG, "📝 Creando incidente: ${incident.type} - ${incident.category}")

            val request = CreateIncidentReq(
                categoryGroup = incident.type.name,
                type = incident.category,
                title = incident.category,
                description = incident.description,
                latitude = incident.location.latitude,
                longitude = incident.location.longitude,
                address = incident.address
            )

            val response = api.createIncident("Bearer $token", request)

            Log.d(TAG, "✅ Incidente creado: ${response._id}")

            Result.success(response._id)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error creando incidente: ${e.message}", e)
            Result.failure(e)
        }
    }

    // ==========================================
    // CONFIRMAR INCIDENTE
    // ==========================================

    suspend fun confirmIncident(incidentId: String): Result<Unit> {
        return try {
            Log.d(TAG, "✅ Confirmando incidente: $incidentId")

            val token = getToken() ?: return Result.failure(Exception("No autenticado"))

            api.confirmIncident("Bearer $token", incidentId)

            Log.d(TAG, "✅ Incidente confirmado")

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error confirmando: ${e.message}", e)

            // Extraer mensaje de error específico del backend
            val errorMsg = when {
                e.message?.contains("Ya confirmaste") == true -> "Ya confirmaste este incidente"
                else -> e.message ?: "Error confirmando"
            }

            Result.failure(Exception(errorMsg))
        }
    }

    // ==========================================
    // ✅ NUEVO: DESCONFIRMAR INCIDENTE
    // ==========================================

    suspend fun unconfirmIncident(incidentId: String): Result<Unit> {
        return try {
            Log.d(TAG, "❌ Desconfirmando incidente: $incidentId")

            val token = getToken() ?: return Result.failure(Exception("No autenticado"))

            api.unconfirmIncident("Bearer $token", incidentId)

            Log.d(TAG, "✅ Confirmación removida")

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error desconfirmando: ${e.message}", e)

            val errorMsg = when {
                e.message?.contains("No has confirmado") == true -> "No has confirmado este incidente"
                else -> e.message ?: "Error removiendo confirmación"
            }

            Result.failure(Exception(errorMsg))
        }
    }

    // ==========================================
    // AGREGAR COMENTARIO
    // ==========================================

    suspend fun addComment(incidentId: String, text: String): Result<Unit> {
        return try {
            val token = getToken() ?: return Result.failure(Exception("No autenticado"))

            val comment = CommentRequest(text)
            api.addComment("Bearer $token", incidentId, comment)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error agregando comentario: ${e.message}", e)
            Result.failure(e)
        }
    }

    // ==========================================
    // ELIMINAR INCIDENTE
    // ==========================================

    suspend fun deleteIncident(incidentId: String): Result<Unit> {
        return try {
            val token = getToken() ?: return Result.failure(Exception("No autenticado"))

            api.deleteIncident("Bearer $token", incidentId)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error eliminando: ${e.message}", e)
            Result.failure(e)
        }
    }

    // ==========================================
    // HELPERS
    // ==========================================

    private suspend fun getToken(): String? {
        return TokenStore.get() ?: run {
            TokenStore.refresh()
            TokenStore.get()
        }
    }

    // ==========================================
    // MAPPERS: Backend Response → App Model
    // ==========================================

    private fun IncidentResp.toIncident(): Incident {
        val lng = location.coordinates.getOrNull(0) ?: 0.0
        val lat = location.coordinates.getOrNull(1) ?: 0.0

        return Incident(
            id = _id,
            type = when (categoryGroup.uppercase()) {
                "SEGURIDAD" -> IncidentType.SEGURIDAD
                "INFRAESTRUCTURA" -> IncidentType.INFRAESTRUCTURA
                else -> IncidentType.SEGURIDAD
            },
            category = type,
            description = description,
            location = GeoPoint(lat, lng),
            address = address ?: "",
            imageUrl = null,
            userId = reporterUid,
            userName = "Usuario",
            timestamp = parseTimestamp(createdAt),
            verified = confirmationsCount >= 3,
            confirmations = confirmationsCount,
            confirmedBy = confirmedBy ?: emptyList()
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