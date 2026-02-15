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
    val userId: String = "",
    val userName: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val verified: Boolean = false,
    val confirmations: Int = 0,
    val confirmedBy: List<String> = emptyList()
)

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