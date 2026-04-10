package com.example.safecity.screens


import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    isLoggedIn: Boolean,
    goHome: () -> Unit,
    goLogin: () -> Unit
) {
    LaunchedEffect(Unit) {
        delay(400) // mini splash
        if (isLoggedIn) goHome() else goLogin()
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}