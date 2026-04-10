package com.example.safecity.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.safecity.MainActivity
import com.example.safecity.R
import com.example.safecity.network.DeviceSyncManager
import com.example.safecity.store.UserPreferencesStore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FCMService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)

        CoroutineScope(Dispatchers.IO).launch {
            Log.d("FCMService", "TOKEN_FCM_OBTENIDO token=$token origen=onNewToken")
            DeviceSyncManager.syncDevice(
                context = applicationContext,
                fcmTokenOverride = token
            )
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        // Inicializar preferencias si el servicio arranca sin MainActivity (app cerrada/background)
        UserPreferencesStore.init(applicationContext)

        // Respetar preferencia de notificaciones del usuario
        if (!UserPreferencesStore.enableNotifications) {
            Log.d("FCMService", "Notificaciones desactivadas por el usuario, omitiendo")
            return
        }

        // Extraer incidentId del payload de datos (prioridad: data > notification)
        val data = remoteMessage.data
        val incidentId = data["incidentId"]?.takeIf { it.isNotBlank() }
            ?: data["reportId"]?.takeIf { it.isNotBlank() }

        // Obtener título y cuerpo: primero desde data (permite override desde servidor),
        // luego desde el bloque notification
        val title = data["title"]?.takeIf { it.isNotBlank() }
            ?: remoteMessage.notification?.title
            ?: "SafeCity"

        val body = data["body"]?.takeIf { it.isNotBlank() }
            ?: remoteMessage.notification?.body
            ?: ""

        if (title.isNotBlank() || body.isNotBlank()) {
            showNotification(title, body, incidentId)
        }
    }

    private fun showNotification(title: String, message: String, incidentId: String? = null) {
        val channelId = "safecity_incidents"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Crear canal para Android 8+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Incidentes SafeCity",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones de nuevos incidentes cercanos"
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Intent para abrir la app; si hay incidentId se incluye como extra para navegación
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            if (!incidentId.isNullOrBlank()) {
                putExtra(MainActivity.EXTRA_INCIDENT_ID, incidentId)
            }
        }

        // Usar incidentId como request code para que cada reporte tenga su propio PendingIntent
        val requestCode = incidentId?.hashCode() ?: 0
        val pendingIntent = PendingIntent.getActivity(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
