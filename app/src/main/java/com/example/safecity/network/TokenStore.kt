package com.example.safecity.network

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

object TokenStore {
    @Volatile private var cachedToken: String? = null

    fun get(): String? = cachedToken
    fun clear() { cachedToken = null }

    /** Úsalo después de login/register (forceRefresh=true) o al iniciar app (false). */
    suspend fun refresh(forceRefresh: Boolean = false): String? {
        val user = FirebaseAuth.getInstance().currentUser ?: run {
            cachedToken = null
            return null
        }

        return try {
            val token = user.getIdToken(forceRefresh).await().token
            cachedToken = token
            token
        } catch (e: Exception) {
            // no bloquees la app si falla
            cachedToken = null
            null
        }
    }
}