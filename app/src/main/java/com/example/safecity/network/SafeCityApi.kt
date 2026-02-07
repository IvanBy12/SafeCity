package com.example.safecity.network

import retrofit2.http.*

data class BootstrapBody(val displayName: String? = null, val photoUrl: String? = null)

data class MeResponse(
    val firebaseUid: String,
    val role: String,
    val status: String,
    val email: String?,
    val isAdmin: Boolean
)

interface SafeCityApi {

    // Si authBootstrap router está montado en "/" y la route es "/auth/bootstrap":
    @POST("auth/bootstrap")
    suspend fun bootstrap(
        @Header("Authorization") auth: String,
        @Body body: BootstrapBody
    ): Map<String, Any>

    @GET("me")
    suspend fun me(
        @Header("Authorization") auth: String
    ): MeResponse

    // Incidents.js asumiendo que el router se montó con app.use("/incidents", incidentsRouter)
    @GET("incidents")
    suspend fun listIncidents(@Header("Authorization") auth: String): List<Map<String, Any>>

    @GET("incidents/near")
    suspend fun listNearby(
        @Header("Authorization") auth: String,
        @Query("lat") lat: Double,
        @Query("lng") lng: Double,
        @Query("radius") radius: Int
    ): List<Map<String, Any>>

    @GET("incidents/{id}")
    suspend fun incidentDetail(
        @Header("Authorization") auth: String,
        @Path("id") id: String
    ): Map<String, Any>
}
