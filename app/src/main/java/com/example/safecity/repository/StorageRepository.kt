package com.example.safecity.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.util.UUID

class StorageRepository(
    private val storage: FirebaseStorage = FirebaseStorage.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    private val TAG = "StorageRepository"

    /**
     * Sube una imagen a Firebase Storage y retorna la URL pública de descarga.
     */
    suspend fun uploadIncidentPhoto(context: Context, imageUri: Uri): Result<String> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.failure(Exception("No autenticado"))
            val fileName = "incidents/${uid}/${UUID.randomUUID()}.jpg"
            val ref = storage.reference.child(fileName)

            Log.d(TAG, "Subiendo imagen: $fileName")

            // Subir archivo
            ref.putFile(imageUri).await()

            // Obtener URL de descarga
            val downloadUrl = ref.downloadUrl.await().toString()

            Log.d(TAG, "Imagen subida exitosamente: $downloadUrl")
            Result.success(downloadUrl)
        } catch (e: Exception) {
            Log.e(TAG, "Error subiendo imagen: ${e.message}", e)
            Result.failure(e)
        }
    }
}