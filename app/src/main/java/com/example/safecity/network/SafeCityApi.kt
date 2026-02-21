package com.example.safecity.network

import com.google.gson.JsonElement
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
    val address: String? = null,
    val photos: List<String> = emptyList()
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

data class PaginatedIncidentsResponse(
    val success: Boolean,
    val page: Int,
    val limit: Int,
    val total: Int,
    val count: Int,
    val data: List<IncidentResp>
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
    // ✅ JsonElement para manejar tanto strings como objetos en photos
    val photos: JsonElement?,
    // Nuevo sistema de validación
    val validationScore: Int?,
    val votedTrue: List<String>?,
    val votedFalse: List<String>?,
    val verified: Boolean?,
    val flaggedFalse: Boolean?,
    // Compatibilidad
    val confirmationsCount: Int,
    val confirmedBy: List<String>?,
    val commentsCount: Int?,
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

data class VoteResponse(
    val success: Boolean,
    val message: String?,
    val error: String?,
    val data: VoteData?
)

data class VoteData(
    val validationScore: Int,
    val votedTrue: Int,
    val votedFalse: Int,
    val verified: Boolean,
    val flaggedFalse: Boolean,
    val status: String
)

// ==========================================
// API INTERFACE
// ==========================================

interface SafeCityApi {

    @GET("incidents")
    suspend fun listIncidents(
        @Header("Authorization") auth: String
    ): PaginatedIncidentsResponse

    @GET("incidents/near")
    suspend fun listNearby(
        @Header("Authorization") auth: String,
        @Query("lat") lat: Double,
        @Query("lng") lng: Double,
        @Query("radius") radiusKm: Int = 5
    ): PaginatedIncidentsResponse

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

    @PUT("incidents/{id}/vote/true")
    suspend fun voteTrue(
        @Header("Authorization") auth: String,
        @Path("id") id: String
    ): VoteResponse

    @PUT("incidents/{id}/vote/false")
    suspend fun voteFalse(
        @Header("Authorization") auth: String,
        @Path("id") id: String
    ): VoteResponse

    @DELETE("incidents/{id}/vote")
    suspend fun removeVote(
        @Header("Authorization") auth: String,
        @Path("id") id: String
    ): VoteResponse

    @PUT("incidents/{id}/confirm")
    suspend fun confirmIncident(
        @Header("Authorization") auth: String,
        @Path("id") id: String
    ): VoteResponse

    @DELETE("incidents/{id}/confirm")
    suspend fun unconfirmIncident(
        @Header("Authorization") auth: String,
        @Path("id") id: String
    ): VoteResponse

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