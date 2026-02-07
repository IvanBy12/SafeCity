package com.example.safecity.network

import retrofit2.http.GET

data class PingResponse(val ok: Boolean, val message: String? = null)

interface SafeCityApi {
    @GET("ping")
    suspend fun ping(): PingResponse
}
