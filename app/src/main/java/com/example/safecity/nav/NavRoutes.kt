package com.example.safecity.nav

object Routes {
    const val Splash = "splash"
    const val Login = "login"
    const val Register = "register"
    const val PhoneAuth = "phone_auth"
    const val Home = "home"
    const val CreateIncident = "create_incident"
    const val IncidentDetail = "incident_detail/{incidentId}"
    const val MyReports = "my_reports"
    const val Profile = "profile"
    const val Statistics = "statistics"

    fun incidentDetail(incidentId: String) = "incident_detail/$incidentId"
}


