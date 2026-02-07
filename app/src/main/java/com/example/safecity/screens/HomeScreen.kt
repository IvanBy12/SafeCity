package com.example.safecity.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import com.example.safecity.screens.dashboard.DashboardScreen


@Composable
fun HomeScreen(onLogout: () -> Unit) {
    DashboardScreen(onLogout = onLogout)
}



