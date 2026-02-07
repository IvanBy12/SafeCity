package com.example.safecity.repository

import com.example.safecity.models.Incident
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class IncidentRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    private val incidentsCollection = db.collection("incidents")

    // ✅ Listener en tiempo real de todos los incidentes
    fun getIncidentsFlow(): Flow<List<Incident>> = callbackFlow {
        val listener = incidentsCollection
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                val incidents = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Incident::class.java)?.copy(id = doc.id)
                } ?: emptyList()

                trySend(incidents)
            }

        awaitClose { listener.remove() }
    }

    // ✅ Crear nuevo incidente
    suspend fun createIncident(incident: Incident): Result<String> {
        return try {
            val user = auth.currentUser
            if (user == null) {
                return Result.failure(Exception("Usuario no autenticado"))
            }

            val incidentWithUser = incident.copy(
                userId = user.uid,
                userName = user.displayName ?: user.email ?: "Usuario Anónimo",
                timestamp = System.currentTimeMillis()
            )

            val docRef = incidentsCollection.add(incidentWithUser).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ✅ Confirmar incidente (validación comunitaria)
    suspend fun confirmIncident(incidentId: String): Result<Unit> {
        return try {
            val userId = auth.currentUser?.uid
            if (userId == null) {
                return Result.failure(Exception("No autenticado"))
            }

            db.runTransaction { transaction ->
                val docRef = incidentsCollection.document(incidentId)
                val snapshot = transaction.get(docRef)

                val confirmedBy = snapshot.get("confirmedBy") as? List<*> ?: emptyList<String>()

                // Evitar confirmaciones duplicadas
                if (userId in confirmedBy) {
                    throw Exception("Ya confirmaste este incidente")
                }

                val newConfirmedBy = confirmedBy + userId
                val newCount = newConfirmedBy.size

                // Verificar automáticamente si tiene 3+ confirmaciones
                transaction.update(docRef, mapOf(
                    "confirmedBy" to newConfirmedBy,
                    "confirmations" to newCount,
                    "verified" to (newCount >= 3)
                ))
            }.await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ✅ Eliminar incidente (solo el creador)
    suspend fun deleteIncident(incidentId: String): Result<Unit> {
        return try {
            val userId = auth.currentUser?.uid
            if (userId == null) {
                return Result.failure(Exception("No autenticado"))
            }

            val doc = incidentsCollection.document(incidentId).get().await()
            val incident = doc.toObject(Incident::class.java)

            if (incident?.userId != userId) {
                return Result.failure(Exception("Solo puedes eliminar tus propios reportes"))
            }

            incidentsCollection.document(incidentId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}