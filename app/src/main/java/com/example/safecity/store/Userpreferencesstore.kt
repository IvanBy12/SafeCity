package com.example.safecity.store

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Almacena preferencias de usuario de forma persistente con SharedPreferences.
 * Singleton que se inicializa una vez con el ApplicationContext.
 *
 * Preferencias que maneja:
 * - defaultAnonymous: si los reportes se publican anónimos por defecto
 * - enableNotifications: si el usuario quiere recibir notificaciones
 * - shareLocationAlways: si incluye ubicación exacta en los reportes
 */
object UserPreferencesStore {

    private const val PREFS_NAME = "safecity_user_prefs"
    private const val KEY_DEFAULT_ANONYMOUS = "default_anonymous"
    private const val KEY_ENABLE_NOTIFICATIONS = "enable_notifications"
    private const val KEY_SHARE_LOCATION = "share_location_always"

    private lateinit var prefs: SharedPreferences

    /** Llamar una sola vez desde Application o MainActivity antes de usar el store */
    fun init(context: Context) {
        prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // ==========================================
    // ANÓNIMO POR DEFECTO
    // ==========================================

    var defaultAnonymous: Boolean
        get() = prefs.getBoolean(KEY_DEFAULT_ANONYMOUS, false)
        set(value) = prefs.edit { putBoolean(KEY_DEFAULT_ANONYMOUS, value) }

    // ==========================================
    // NOTIFICACIONES
    // ==========================================

    var enableNotifications: Boolean
        get() = prefs.getBoolean(KEY_ENABLE_NOTIFICATIONS, true)
        set(value) = prefs.edit { putBoolean(KEY_ENABLE_NOTIFICATIONS, value) }

    // ==========================================
    // UBICACIÓN
    // ==========================================

    var shareLocationAlways: Boolean
        get() = prefs.getBoolean(KEY_SHARE_LOCATION, false)
        set(value) = prefs.edit { putBoolean(KEY_SHARE_LOCATION, value) }
}