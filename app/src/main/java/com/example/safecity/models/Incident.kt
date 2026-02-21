package com.example.safecity.models

import com.google.firebase.firestore.GeoPoint

data class Incident(
    val id: String = "",
    val type: IncidentType = IncidentType.SEGURIDAD,
    val category: String = "",
    val description: String = "",
    val location: GeoPoint = GeoPoint(0.0, 0.0),
    val address: String = "",
    val imageUrl: String? = null,
    val photos: List<String> = emptyList(),
    val userId: String = "",
    val userName: String = "",
    val timestamp: Long = System.currentTimeMillis(),

    // ========================================
    // NUEVO SISTEMA DE VALIDACIÓN
    // ========================================
    val validationScore: Int = 0,        // neto = votedTrue - votedFalse
    val votedTrueCount: Int = 0,         // cantidad de votos "verdadero"
    val votedFalseCount: Int = 0,        // cantidad de votos "falso"
    val verified: Boolean = false,        // true si validationScore >= 3
    val flaggedFalse: Boolean = false,    // true si validationScore <= -5
    val userVoteStatus: String = "none",  // "none" | "true" | "false"

    // Compatibilidad
    val confirmations: Int = 0,
    val confirmedBy: List<String> = emptyList(),
    val votedTrue: List<String> = emptyList(),
    val votedFalse: List<String> = emptyList()
) {
    /** Retorna la primera foto disponible o null */
    val firstPhoto: String?
        get() = photos.firstOrNull() ?: imageUrl
}

enum class IncidentType {
    SEGURIDAD,
    INFRAESTRUCTURA
}

object IncidentCategories {
    val SEGURIDAD = listOf(
        "Robo",
        "Asalto",
        "Acoso",
        "Vandalismo",
        "Sospechoso",
        "Otro"
    )

    val INFRAESTRUCTURA = listOf(
        "Bache",
        "Alumbrado",
        "Basura",
        "Alcantarilla",
        "Semáforo",
        "Otro"
    )
}