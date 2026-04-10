package com.example.safecity.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.util.UUID

object ImageUploadHelper {

    private val TAG = "ImageUploadHelper"

    // Referencia al bucket correcto
    private val storage = FirebaseStorage.getInstance("gs://safecity-9a387.firebasestorage.app")
    private val storageRef = storage.reference

    /**
     * Sube una imagen a Firebase Storage y retorna la URL pública.
     *
     * Ruta: incidents/{userId}/{uuid}.jpg
     *
     * La imagen se comprime a JPEG 70% y se redimensiona a max 1024px
     * para no gastar mucho almacenamiento.
     */
    suspend fun uploadIncidentPhoto(
        context: Context,
        imageUri: Uri
    ): Result<String> {
        return try {
            val userId = FirebaseAuth.getInstance().currentUser?.uid
                ?: return Result.failure(Exception("No autenticado"))

            Log.d(TAG, "Comprimiendo imagen...")

            // 1) Leer y comprimir la imagen
            val compressedBytes = compressImage(context, imageUri)
                ?: return Result.failure(Exception("No se pudo leer la imagen"))

            Log.d(TAG, "Imagen comprimida: ${compressedBytes.size / 1024} KB")

            // 2) Generar ruta única
            val fileName = "${UUID.randomUUID()}.jpg"
            val photoRef = storageRef.child("incidents/$userId/$fileName")

            Log.d(TAG, "Subiendo a: incidents/$userId/$fileName")

            // 3) Subir a Firebase Storage
            val uploadTask = photoRef.putBytes(compressedBytes)
            uploadTask.await()

            // 4) Obtener URL de descarga
            val downloadUrl = photoRef.downloadUrl.await().toString()

            Log.d(TAG, "Imagen subida exitosamente: $downloadUrl")

            Result.success(downloadUrl)

        } catch (e: Exception) {
            Log.e(TAG, "Error subiendo imagen: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Comprime la imagen a JPEG 70%, max 1024px de lado mayor.
     */
    private fun compressImage(context: Context, uri: Uri): ByteArray? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return null

            // Primero, obtener dimensiones sin cargar toda la imagen
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            val tempStream = context.contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(tempStream, null, options)
            tempStream?.close()

            // Calcular inSampleSize para redimensionar
            val maxDimension = 1024
            options.inSampleSize = calculateInSampleSize(
                options.outWidth,
                options.outHeight,
                maxDimension,
                maxDimension
            )
            options.inJustDecodeBounds = false

            // Decodificar con el tamaño reducido
            val stream2 = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(stream2, null, options)
            stream2?.close()

            if (bitmap == null) {
                inputStream.close()
                return null
            }

            // Comprimir a JPEG 70%
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
            bitmap.recycle()

            inputStream.close()

            outputStream.toByteArray()
        } catch (e: Exception) {
            Log.e(TAG, "Error comprimiendo imagen: ${e.message}", e)
            null
        }
    }

    private fun calculateInSampleSize(
        width: Int, height: Int,
        reqWidth: Int, reqHeight: Int
    ): Int {
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight &&
                (halfWidth / inSampleSize) >= reqWidth
            ) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    /**
     * Elimina una imagen de Firebase Storage dado su URL.
     */
    suspend fun deletePhoto(downloadUrl: String): Result<Unit> {
        return try {
            val ref = FirebaseStorage.getInstance("gs://safecity-9a387.firebasestorage.app")
                .getReferenceFromUrl(downloadUrl)
            ref.delete().await()
            Log.d(TAG, "Imagen eliminada: $downloadUrl")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error eliminando imagen: ${e.message}", e)
            Result.failure(e)
        }
    }
}