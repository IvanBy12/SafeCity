package com.example.safecity.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.safecity.MainActivity
import com.example.safecity.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class FCMService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        // ✅ Si viene notificación
        remoteMessage.notification?.let {
            showNotification(it.title ?: "SafeCity", it.body ?: "")
        }

        // ✅ Si vienen datos
        remoteMessage.data.isNotEmpty().let {
            // Aquí puedes procesar datos adicionales
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // ✅ Aquí puedes guardar el token en Firestore si quieres notificaciones dirigidas
        // Ejemplo: guardar en /users/{userId}/fcmToken
    }

    private fun showNotification(title: String, message: String) {
        val channelId = "safecity_incidents"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // ✅ Crear canal para Android 8+
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

        // ✅ Intent para abrir la app al tocar
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        // ✅ Construir notificación
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.mipmap.ic_launcher) // Usa tu icono
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}