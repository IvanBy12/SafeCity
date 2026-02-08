package com.example.safecity.screens

import androidx.compose.runtime.Composable
import com.example.safecity.screens.dashboard.DashboardScreen

@Composable
fun HomeScreen(
    onLogout: () -> Unit,
    onNavigateToCreateIncident: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onNavigateToIncidentDetail: (String) -> Unit = {}
) {
    DashboardScreen(
        onLogout = onLogout,
        onNavigateToCreateIncident = onNavigateToCreateIncident,
        onNavigateToProfile = onNavigateToProfile,
        onNavigateToIncidentDetail = onNavigateToIncidentDetail
    )
}