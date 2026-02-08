package com.example.safecity.network

import retrofit2.http.*

// ==========================================
// REQUEST BODIES
// ==========================================

data class CreateIncidentReq(
    val categoryGroup: String,
    val type: String,
    val title: String,
    val description: String,
    val latitude: Double,
    val longitude: Double,
    val address: String? = null
)

data class VoteRequest(
    val voteType: String = "upvote"
)

data class CommentRequest(
    val text: String
)

// ==========================================
// RESPONSE MODELS
// ==========================================

// ✅ NUEVO: Response con paginación
data class PaginatedIncidentsResponse(
    val success: Boolean,
    val page: Int,
    val limit: Int,
    val total: Int,
    val count: Int,
    val data: List<IncidentResp>  // Los incidentes están en "data"
)

data class IncidentResp(
    val _id: String,
    val categoryGroup: String,
    val type: String,
    val title: String,
    val description: String,
    val location: LocationResponse,
    val address: String?,
    val reporterUid: String,
    val status: String,
    val confirmationsCount: Int,
    val confirmedBy: List<String>?, //FIX: Hacer nullable
    val createdAt: String,
    val updatedAt: String
)

data class LocationResponse(
    val type: String,
    val coordinates: List<Double>
)

data class StatsResponse(
    val total: Int,
    val verified: Int,
    val byType: Map<String, Int>
)

// ==========================================
// API INTERFACE
// ==========================================

interface SafeCityApi {

    // ✅ ACTUALIZADO: Ahora devuelve PaginatedIncidentsResponse
    @GET("incidents")
    suspend fun listIncidents(
        @Header("Authorization") auth: String
    ): PaginatedIncidentsResponse  // Cambio aquí

    @GET("incidents/near")
    suspend fun listNearby(
        @Header("Authorization") auth: String,
        @Query("lat") lat: Double,
        @Query("lng") lng: Double,
        @Query("radius") radiusKm: Int = 5
    ): PaginatedIncidentsResponse  // Y aquí

    @GET("incidents/stats")
    suspend fun getStats(
        @Header("Authorization") auth: String
    ): StatsResponse

    @GET("incidents/{id}")
    suspend fun getIncidentDetail(
        @Header("Authorization") auth: String,
        @Path("id") id: String
    ): IncidentResp

    @POST("incidents")
    suspend fun createIncident(
        @Header("Authorization") auth: String,
        @Body request: CreateIncidentReq
    ): IncidentResp

    @PUT("incidents/{id}/confirm")
    suspend fun confirmIncident(
        @Header("Authorization") auth: String,
        @Path("id") id: String
    ): IncidentResp

    @DELETE("incidents/{id}/confirm")
    suspend fun unconfirmIncident(
        @Header("Authorization") auth: String,
        @Path("id") id: String
    ): IncidentResp

    @POST("incidents/{id}/votes")
    suspend fun voteIncident(
        @Header("Authorization") auth: String,
        @Path("id") id: String,
        @Body vote: VoteRequest
    ): IncidentResp

    @POST("incidents/{id}/comments")
    suspend fun addComment(
        @Header("Authorization") auth: String,
        @Path("id") id: String,
        @Body comment: CommentRequest
    ): IncidentResp

    @DELETE("incidents/{id}")
    suspend fun deleteIncident(
        @Header("Authorization") auth: String,
        @Path("id") id: String
    ): Map<String, Any>
}