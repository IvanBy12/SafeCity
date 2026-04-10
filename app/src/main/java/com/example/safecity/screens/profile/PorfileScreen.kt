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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.safecity.store.UserPreferencesStore
import com.google.firebase.auth.FirebaseAuth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onNavigateBack: () -> Unit,
    onNavigateToMyReports: () -> Unit,
    onNavigateToStatistics: () -> Unit,
    onLogout: () -> Unit
) {
    val auth        = FirebaseAuth.getInstance()
    val currentUser = auth.currentUser

    // ─── REQ 1: preferencias leídas desde SharedPreferences ───────────────
    // Cada cambio de switch se persiste inmediatamente
    var defaultAnonymous    by remember { mutableStateOf(UserPreferencesStore.defaultAnonymous) }
    var enableNotifications by remember { mutableStateOf(UserPreferencesStore.enableNotifications) }
    var shareLocation       by remember { mutableStateOf(UserPreferencesStore.shareLocationAlways) }
    var showLogoutDialog    by remember { mutableStateOf(false) }

    // Detectar si es admin por email (ajustar según tu lógica de roles)
    val isAdmin = currentUser?.email?.endsWith("@safecity.com") == true

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mi Perfil") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, "Volver")
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
            // ─── CABECERA ──────────────────────────────────────────────
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color    = MaterialTheme.colorScheme.primaryContainer
            ) {
                Column(
                    modifier              = Modifier.padding(24.dp),
                    horizontalAlignment   = Alignment.CenterHorizontally,
                    verticalArrangement   = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Person, null,
                            Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    Text(
                        currentUser?.displayName ?: "Usuario",
                        style      = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        currentUser?.email ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    // Badge modo anónimo activo
                    if (defaultAnonymous) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Row(
                                modifier              = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment     = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.VisibilityOff, null,
                                    Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    "Modo anónimo activo",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }
            }

            // ─── MIS REPORTES ──────────────────────────────────────────
            SectionTitle("Mis Reportes")
            ProfileItem(
                icon     = Icons.Filled.ListAlt,
                title    = "Ver mis reportes",
                subtitle = "Historial, estado y seguimiento",
                onClick  = onNavigateToMyReports
            )
            Divider()

            // ─── REQ 1: PRIVACIDAD ─────────────────────────────────────
            SectionTitle("Privacidad")

            // Anónimo por defecto — afecta a TODOS los reportes y comentarios futuros
            SwitchItem(
                icon     = Icons.Filled.VisibilityOff,
                title    = "Publicar reportes anónimos",
                subtitle = if (defaultAnonymous)
                    "Tus reportes no mostrarán tu nombre. Puedes cambiar esto por reporte."
                else
                    "Tu nombre aparecerá en tus reportes. Puedes ocultarlo al crear.",
                checked  = defaultAnonymous,
                onChange = { v ->
                    defaultAnonymous = v
                    UserPreferencesStore.defaultAnonymous = v  // ← persiste
                }
            )
            Divider()
            SwitchItem(
                icon     = Icons.Filled.LocationOn,
                title    = "Compartir ubicación exacta",
                subtitle = "Incluir coordenadas precisas en los reportes",
                checked  = shareLocation,
                onChange = { v ->
                    shareLocation = v
                    UserPreferencesStore.shareLocationAlways = v
                }
            )
            Divider()

            // ─── REQ 1: NOTIFICACIONES ────────────────────────────────
            SectionTitle("Notificaciones")
            SwitchItem(
                icon     = Icons.Filled.NotificationsActive,
                title    = "Alertas de proximidad",
                subtitle = "Recibir notificaciones de incidentes a menos de 500 m",
                checked  = enableNotifications,
                onChange = { v ->
                    enableNotifications = v
                    UserPreferencesStore.enableNotifications = v
                }
            )
            Divider()

            // ─── ADMIN ────────────────────────────────────────────────
            if (isAdmin) {
                SectionTitle("Administración")
                ProfileItem(
                    icon     = Icons.Filled.BarChart,
                    title    = "Estadísticas",
                    subtitle = "Datos agregados para entidades distritales",
                    onClick  = onNavigateToStatistics
                )
                Divider()
            }

            // ─── CUENTA ───────────────────────────────────────────────
            SectionTitle("Cuenta")
            ProfileItem(
                icon     = Icons.Filled.Info,
                title    = "Acerca de SafeCity",
                subtitle = "Versión 1.0.0",
                onClick  = {}
            )
            Divider()
            ProfileItem(
                icon     = Icons.Filled.Logout,
                title    = "Cerrar sesión",
                subtitle = "",
                onClick  = { showLogoutDialog = true },
                tint     = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(32.dp))
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            icon    = { Icon(Icons.Filled.Logout, null) },
            title   = { Text("Cerrar sesión") },
            text    = { Text("¿Estás seguro que deseas cerrar sesión?") },
            confirmButton = {
                TextButton(onClick = { showLogoutDialog = false; onLogout() }) {
                    Text("Cerrar sesión", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("Cancelar") }
            }
        )
    }
}

// ==========================================
// COMPONENTES INTERNOS
// ==========================================

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        modifier   = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        style      = MaterialTheme.typography.titleSmall,
        color      = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun ProfileItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    ListItem(
        headlineContent   = { Text(title, color = tint) },
        supportingContent = if (subtitle.isNotBlank()) ({ Text(subtitle) }) else null,
        leadingContent    = { Icon(icon, null, tint = tint) },
        trailingContent   = { Icon(Icons.Filled.ChevronRight, null) },
        modifier          = Modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun SwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent   = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent    = { Icon(icon, null) },
        trailingContent   = { Switch(checked = checked, onCheckedChange = onChange) }
    )
}