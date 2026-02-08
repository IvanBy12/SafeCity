<<<<<<< Updated upstream
package com.example.safecity.navegation

=======
package com.example.safecity.navigation

sealed class Screen(val route: String) {
    // Autenticación
    object Login : Screen("login")

    // Principal
    object Dashboard : Screen("dashboard")

    // Perfil y reportes
    object Profile : Screen("profile")
    object MyReports : Screen("my_reports")

    // Detalle
    object IncidentDetail : Screen("incident_detail/{incidentId}") {
        fun createRoute(incidentId: String) = "incident_detail/$incidentId"
    }

    // Administración
    object Statistics : Screen("statistics")
}
>>>>>>> Stashed changes
