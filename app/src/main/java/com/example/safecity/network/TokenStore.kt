package com.example.safecity.network

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

object TokenStore {
    private const val TAG = "TokenStore"

    @Volatile private var cachedToken: String? = null

    // Firebase ID tokens duran 1 hora (3600 seg).
    // Refrescamos si faltan menos de 5 minutos para expirar.
    @Volatile private var tokenExpiresAt: Long = 0L
    private const val REFRESH_MARGIN_MS = 5 * 60 * 1000L // 5 min antes

    fun get(): String? {
        // Si el token está próximo a expirar, lo descartamos para forzar refresh
        if (cachedToken != null && System.currentTimeMillis() >= tokenExpiresAt - REFRESH_MARGIN_MS) {
            Log.d(TAG, "Token próximo a expirar, descartando cache")
            cachedToken = null
        }
        return cachedToken
    }

    fun clear() {
        cachedToken = null
        tokenExpiresAt = 0L
    }

    /**
     * Obtiene un token fresco desde Firebase.
     * Si [forceRefresh] = true siempre va a la red aunque el cache sea válido.
     */
    suspend fun refresh(forceRefresh: Boolean = false): String? {
        val user = FirebaseAuth.getInstance().currentUser ?: run {
            Log.w(TAG, "refresh() llamado sin usuario autenticado")
            cachedToken = null
            tokenExpiresAt = 0L
            return null
        }

        return try {
            val result = user.getIdToken(forceRefresh).await()
            val token = result.token
            if (!token.isNullOrBlank()) {
                cachedToken = token
                // Firebase nos da el tiempo de expiración en segundos desde epoch
                val expirationTime = result.expirationTimestamp * 1000L // a millis
                tokenExpiresAt = if (expirationTime > 0) expirationTime
                else System.currentTimeMillis() + 60 * 60 * 1000L // fallback: +1h
                Log.d(TAG, "Token refrescado OK, expira en ${(tokenExpiresAt - System.currentTimeMillis()) / 60000} min")
            }
            token
        } catch (e: Exception) {
            Log.e(TAG, "Error refrescando token: ${e.message}")
            cachedToken = null
            tokenExpiresAt = 0L
            null
        }
    }

    /**
     * Retorna el token actual si es válido, o refresca y retorna uno nuevo.
     * Shortcut para evitar el patrón get() ?: refresh() regado por el código.
     */
    suspend fun getOrRefresh(): String? = get() ?: refresh(forceRefresh = true)
}