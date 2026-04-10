package com.example.safecity.network

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

object DeviceSyncManager {

    private const val TAG = "DeviceSyncManager"

    suspend fun syncDevice(
        context: Context,
        latitude: Double? = null,
        longitude: Double? = null,
        fcmTokenOverride: String? = null
    ): Boolean {
        return try {
            val authToken = TokenStore.get() ?: TokenStore.refresh()
            if (authToken.isNullOrBlank()) {
                Log.e(TAG, "TOKEN_FCM_BACKEND_ERROR no_auth_token")
                return false
            }

            val deviceId = getDeviceId(context)
            val fcmToken = fcmTokenOverride ?: FirebaseMessaging.getInstance().token.await()

            Log.d(TAG, "TOKEN_FCM_OBTENIDO token=$fcmToken deviceId=$deviceId")
            Log.d(
                TAG,
                "TOKEN_FCM_ENVIANDO_BACKEND deviceId=$deviceId latitude=$latitude longitude=$longitude"
            )

            val response = ApiClient.api.registerDevice(
                "Bearer $authToken",
                DeviceRegistrationRequest(
                    deviceId = deviceId,
                    platform = "android",
                    fcmToken = fcmToken,
                    latitude = latitude,
                    longitude = longitude
                )
            )

            if (response.ok) {
                Log.d(TAG, "TOKEN_FCM_BACKEND_OK deviceId=$deviceId")
                true
            } else {
                Log.e(
                    TAG,
                    "TOKEN_FCM_BACKEND_ERROR deviceId=$deviceId message=${response.message ?: "unknown"}"
                )
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "TOKEN_FCM_BACKEND_ERROR ${e.message}", e)
            false
        }
    }

    suspend fun syncLocation(
        context: Context,
        latitude: Double,
        longitude: Double
    ): Boolean {
        return try {
            val authToken = TokenStore.get() ?: TokenStore.refresh()
            if (authToken.isNullOrBlank()) {
                Log.w(TAG, "DEVICE_LOCATION_BACKEND_ERROR no_auth_token")
                return false
            }

            val deviceId = getDeviceId(context)
            val response = ApiClient.api.updateLocation(
                "Bearer $authToken",
                LocationUpdateRequest(
                    deviceId = deviceId,
                    latitude = latitude,
                    longitude = longitude
                )
            )

            if (response.ok) {
                Log.d(
                    TAG,
                    "DEVICE_LOCATION_BACKEND_OK deviceId=$deviceId latitude=$latitude longitude=$longitude"
                )
                true
            } else {
                Log.w(
                    TAG,
                    "DEVICE_LOCATION_BACKEND_REPARANDO deviceId=$deviceId message=${response.message ?: "unknown"}"
                )
                syncDevice(
                    context = context,
                    latitude = latitude,
                    longitude = longitude
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "DEVICE_LOCATION_BACKEND_ERROR ${e.message}", e)
            false
        }
    }

    private fun getDeviceId(context: Context): String {
        return try {
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            ) ?: "unknown"
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo obtener ANDROID_ID: ${e.message}")
            "unknown"
        }
    }
}
