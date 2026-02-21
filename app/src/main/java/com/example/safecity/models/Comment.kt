package com.example.safecity.models

data class Comment(
    val id: String = "",
    val incidentId: String = "",
    val authorUid: String? = null,
    val isAnonymous: Boolean = false,
    val text: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    /** Nombre visible del autor */
    val displayName: String
        get() = if (isAnonymous || authorUid == null) "Anónimo" else "Usuario"

    /** Tiempo relativo (hace X min) */
    fun timeAgo(): String {
        val diff = System.currentTimeMillis() - createdAt
        val minutes = diff / 60000
        val hours = minutes / 60
        val days = hours / 24
        return when {
            minutes < 1 -> "ahora"
            minutes < 60 -> "hace $minutes min"
            hours < 24 -> "hace $hours h"
            days < 7 -> "hace $days d"
            else -> {
                val sdf = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
                sdf.format(java.util.Date(createdAt))
            }
        }
    }
}
