package com.example.safecity.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onNavigateBack: () -> Unit,
    onNavigateToMyReports: () -> Unit,
    onNavigateToStatistics: () -> Unit,
    onLogout: () -> Unit
) {
    val auth = FirebaseAuth.getInstance()
    val currentUser = auth.currentUser

    var defaultAnonymous by remember { mutableStateOf(false) }
    var enableNotifications by remember { mutableStateOf(true) }
    var showLocationAlways by remember { mutableStateOf(false) }

    var showLogoutDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Perfil") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Person,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }

                    Text(
                        currentUser?.displayName ?: "Usuario",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        currentUser?.email ?: "Sin email",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SectionHeader("Mis Reportes")

            ProfileMenuItem(
                icon = Icons.Filled.ListAlt,
                title = "Ver mis reportes",
                subtitle = "Historial de incidentes reportados",
                onClick = onNavigateToMyReports
            )

            Divider(modifier = Modifier.padding(horizontal = 16.dp))

            SectionHeader("Privacidad")

            SwitchMenuItem(
                icon = Icons.Filled.VisibilityOff,
                title = "Reportes anónimos por defecto",
                subtitle = "Tus reportes no mostrarán tu nombre",
                checked = defaultAnonymous,
                onCheckedChange = { defaultAnonymous = it }
            )

            Divider(modifier = Modifier.padding(horizontal = 16.dp))

            SwitchMenuItem(
                icon = Icons.Filled.LocationOn,
                title = "Compartir ubicación siempre",
                subtitle = "Incluir ubicación exacta en reportes",
                checked = showLocationAlways,
                onCheckedChange = { showLocationAlways = it }
            )

            Divider(modifier = Modifier.padding(horizontal = 16.dp))

            SectionHeader("Notificaciones")

            SwitchMenuItem(
                icon = Icons.Filled.Notifications,
                title = "Notificaciones",
                subtitle = "Recibir alertas de incidentes cercanos",
                checked = enableNotifications,
                onCheckedChange = { enableNotifications = it }
            )

            Divider(modifier = Modifier.padding(horizontal = 16.dp))

            val isAdmin = currentUser?.email == "admin@safecity.com"

            if (isAdmin) {
                SectionHeader("Administración")

                ProfileMenuItem(
                    icon = Icons.Filled.BarChart,
                    title = "Estadísticas",
                    subtitle = "Panel de análisis y métricas",
                    onClick = onNavigateToStatistics
                )

                Divider(modifier = Modifier.padding(horizontal = 16.dp))
            }

            SectionHeader("Cuenta")

            ProfileMenuItem(
                icon = Icons.Filled.Info,
                title = "Acerca de SafeCity",
                subtitle = "Versión 1.0.0",
                onClick = { }
            )

            Divider(modifier = Modifier.padding(horizontal = 16.dp))

            ProfileMenuItem(
                icon = Icons.Filled.Logout,
                title = "Cerrar sesión",
                subtitle = "",
                onClick = { showLogoutDialog = true },
                color = MaterialTheme.colorScheme.error
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            icon = { Icon(Icons.Filled.Logout, contentDescription = null) },
            title = { Text("Cerrar sesión") },
            text = { Text("¿Estás seguro que deseas cerrar sesión?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        onLogout()
                    }
                ) {
                    Text("Cerrar sesión")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun ProfileMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    ListItem(
        headlineContent = { Text(title, color = color) },
        supportingContent = if (subtitle.isNotBlank()) {
            { Text(subtitle) }
        } else null,
        leadingContent = {
            Icon(icon, contentDescription = null, tint = color)
        },
        trailingContent = {
            Icon(Icons.Filled.ChevronRight, contentDescription = null)
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun SwitchMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = {
            Icon(icon, contentDescription = null)
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    )
}