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
    val photos: List<String> = emptyList(),
    // ─── REQ 1: enviar preferencia de anonimato del usuario ───
    val isAnonymous: Boolean = false
)

data class EditIncidentReq(
    val description: String? = null,
    val photos: List<String>? = null
)

data class VoteRequest(
    val voteType: String = "upvote"
)

data class CommentRequest(
    val text: String,
    // REQ 1: respetar preferencia de anonimato también en comentarios
    val isAnonymous: Boolean = false
)

data class DeviceRegistrationRequest(
    val deviceId: String,
    val platform: String = "android",
    val fcmToken: String,
    val latitude: Double? = null,
    val longitude: Double? = null
)

data class LocationUpdateRequest(
    val deviceId: String,
    val latitude: Double,
    val longitude: Double
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
    val closeDays: Int? = null, // ← REQ 2: cuántos días hasta cierre automático
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
    val reporterUid: String?,           // null si isAnonymous
    val isAnonymous: Boolean = false,   // ← REQ 1
    val status: String,                 // pending | verified | closed
    val photos: JsonElement?,
    val validationScore: Int?,
    val votedTrue: List<String>?,
    val votedFalse: List<String>?,
    val verified: Boolean?,
    val flaggedFalse: Boolean?,
    val confirmationsCount: Int,
    val confirmedBy: List<String>?,
    val commentsCount: Int?,
    val createdAt: String,
    val updatedAt: String,
    val editableUntil: String? = null,  // ← REQ 2: ISO datetime límite de edición
    val isEditable: Boolean? = null,    // ← REQ 2: ya calculado por el backend
    val editSecondsLeft: Int? = null,   // ← REQ 2: segundos restantes de edición
    val daysUntilClose: Int? = null     // ← REQ 2: días hasta cierre automático
)

data class CreateIncidentWrapperResponse(
    val success: Boolean,
    val data: IncidentResp
)

data class IncidentDetailResponse(
    val success: Boolean,
    val data: IncidentDetailData,
    val error: String? = null
)

data class IncidentDetailData(
    val _id: String,
    val categoryGroup: String,
    val type: String,
    val title: String,
    val description: String,
    val location: LocationResponse,
    val address: String?,
    val reporterUid: String?,
    val isAnonymous: Boolean = false,
    val status: String,
    val photos: JsonElement?,
    val validationScore: Int?,
    val votedTrue: List<String>?,
    val votedFalse: List<String>?,
    val verified: Boolean?,
    val flaggedFalse: Boolean?,
    val confirmationsCount: Int?,
    val confirmedBy: List<String>?,
    val commentsCount: Int?,
    val createdAt: String,
    val updatedAt: String,
    val editableUntil: String? = null,
    val comments: List<CommentResp>? = emptyList(),
    val votes: JsonElement? = null
)

data class CommentResp(
    val _id: String,
    val incidentId: String? = null,
    val authorUid: String? = null,
    val isAnonymous: Boolean? = false,
    val text: String,
    val createdAt: String
)

data class SimpleResponse(
    val success: Boolean,
    val message: String? = null,
    val error: String? = null
)

data class OkResponse(
    val ok: Boolean,
    val message: String? = null
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

// ─── REQ 3: respuesta de estadísticas agregadas ───
data class AggregateStatsResponse(
    val success: Boolean,
    val generatedAt: String,
    val period: StatsPeriod,
    val data: AggregateStatsData
)

data class StatsPeriod(
    val type: String,     // "month" | "last12months"
    val year: Int? = null,
    val month: Int? = null
)

data class AggregateStatsData(
    val summary: StatsSummary,
    val byTypeAndStatus: List<TypeStatusStat>,
    val byTimeBand: List<TimeBandStat>,
    val byLocality: List<LocalityStat>,
    val byMonth: List<MonthStat>
)

data class StatsSummary(
    val total: Int,
    val pending: Int,
    val verified: Int,
    val closed: Int,
    val avgScore: Double
)

data class TypeStatusStat(
    val _id: TypeStatusId,
    val count: Int,
    val avgScore: Double
)

data class TypeStatusId(
    val categoryGroup: String,
    val type: String,
    val status: String
)

data class TimeBandStat(
    val _id: TimeBandId,
    val count: Int
)

data class TimeBandId(
    val timeBand: String,
    val categoryGroup: String
)

data class LocalityStat(
    val _id: LocalityId,
    val count: Int,
    val verified: Int
)

data class LocalityId(
    val locality: String,
    val categoryGroup: String
)

data class MonthStat(
    val _id: MonthStatId,
    val count: Int,
    val verified: Int,
    val closed: Int
)

data class MonthStatId(
    val year: Int,
    val month: Int,
    val categoryGroup: String
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

    // ─── Incidentes ───
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

    // REQ 2: mis reportes con estado completo
    @GET("incidents/mine")
    suspend fun getMyIncidents(
        @Header("Authorization") auth: String,
        @Query("status") status: String? = null,
        @Query("page") page: Int? = null,
        @Query("limit") limit: Int? = null
    ): PaginatedIncidentsResponse

    @GET("incidents/stats")
    suspend fun getStats(
        @Header("Authorization") auth: String
    ): StatsResponse

    // REQ 3: estadísticas agregadas sin datos personales
    @GET("incidents/stats/aggregate")
    suspend fun getAggregateStats(
        @Header("Authorization") auth: String,
        @Query("year") year: Int? = null,
        @Query("month") month: Int? = null
    ): AggregateStatsResponse

    @GET("incidents/{id}")
    suspend fun getIncidentDetail(
        @Header("Authorization") auth: String,
        @Path("id") id: String
    ): IncidentDetailResponse

    @POST("incidents")
    suspend fun createIncident(
        @Header("Authorization") auth: String,
        @Body request: CreateIncidentReq
    ): CreateIncidentWrapperResponse

    // REQ 2: edición dentro de plazo
    @PATCH("incidents/{id}")
    suspend fun editIncident(
        @Header("Authorization") auth: String,
        @Path("id") id: String,
        @Body request: EditIncidentReq
    ): IncidentDetailResponse

    @DELETE("incidents/{id}")
    suspend fun deleteIncident(
        @Header("Authorization") auth: String,
        @Path("id") id: String
    ): SimpleResponse

    // ─── Votos ───
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

    // ─── Comentarios ───
    @POST("incidents/{id}/comments")
    suspend fun addComment(
        @Header("Authorization") auth: String,
        @Path("id") id: String,
        @Body comment: CommentRequest
    ): SimpleResponse

    // ─── Dispositivo y ubicación (FCM) ───
    @PUT("me/device")
    suspend fun registerDevice(
        @Header("Authorization") auth: String,
        @Body request: DeviceRegistrationRequest
    ): OkResponse

    @PUT("me/location")
    suspend fun updateLocation(
        @Header("Authorization") auth: String,
        @Body request: LocationUpdateRequest
    ): OkResponse
}

