package com.example.safecity.network

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

data class CreateIncidentRequest(
    val title: String,
    val description: String,
    val lat: Double,
    val lng: Double
)

data class IncidentResponse(
    val id: String,
    val message: String
)

interface ApiService {

    @GET("health")
    suspend fun health(): Map<String, Any>

    // Ejemplo con token manual (recomendado al inicio)
    @POST("incidents")
    suspend fun createIncident(
        @Header("Authorization") auth: String,
        @Body body: CreateIncidentRequest
    ): IncidentResponse
}
