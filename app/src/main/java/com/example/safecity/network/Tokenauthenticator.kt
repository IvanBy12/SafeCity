package com.example.safecity.network

import android.util.Log
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * OkHttp Authenticator: se dispara automáticamente cuando el servidor
 * responde 401 Unauthorized.
 *
 * Flujo:
 *  1. La request original recibe 401
 *  2. OkHttp llama a authenticate()
 *  3. Forzamos refresh del token en Firebase
 *  4. Reintentamos la request con el nuevo token
 *  5. Si el refresh falla o ya reintentamos 2 veces → devolvemos null (no retry)
 */
class TokenAuthenticator : Authenticator {

    private val TAG = "TokenAuthenticator"

    override fun authenticate(route: Route?, response: Response): Request? {
        // Evitar loops: si ya tuvimos 2 intentos con 401 seguidos, paramos
        if (responseCount(response) >= 2) {
            Log.e(TAG, "Demasiados reintentos 401, abortando")
            return null
        }

        Log.d(TAG, "401 recibido, refrescando token...")

        // runBlocking es necesario aquí porque OkHttp llama a authenticate()
        // en un hilo de IO, no en una corrutina
        val newToken = runBlocking {
            TokenStore.refresh(forceRefresh = true)
        }

        if (newToken.isNullOrBlank()) {
            Log.e(TAG, "No se pudo obtener nuevo token, dejando pasar el 401")
            return null
        }

        Log.d(TAG, "Token refrescado, reintentando request")

        // Retorna la misma request pero con el nuevo token
        return response.request.newBuilder()
            .header("Authorization", "Bearer $newToken")
            .build()
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}