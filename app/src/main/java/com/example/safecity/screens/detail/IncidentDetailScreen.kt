package com.example.safecity.screens.detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.safecity.models.Incident
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncidentDetailScreen(
    incidentId: String,
    onNavigateBack: () -> Unit,
    viewModel: IncidentDetailViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(incidentId) {
        viewModel.loadIncident(incidentId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detalle del Incidente") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    // Mostrar opciones solo si es del usuario actual
                    if (uiState.isOwner) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Eliminar")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                uiState.loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                uiState.error != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Filled.ErrorOutline,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Error cargando incidente",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            uiState.error ?: "",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                uiState.incident != null -> {
                    IncidentDetailContent(
                        incident = uiState.incident!!,
                        isOwner = uiState.isOwner,
                        hasUserConfirmed = uiState.hasUserConfirmed,
                        onConfirm = { viewModel.confirmIncident() },
                        onUnconfirm = { viewModel.unconfirmIncident() }
                    )
                }
            }
        }
    }

    // Dialog de confirmación para eliminar
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = { Icon(Icons.Filled.Warning, contentDescription = null) },
            title = { Text("Eliminar reporte") },
            text = { Text("¿Estás seguro que deseas eliminar este reporte? Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteIncident()
                        onNavigateBack()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun IncidentDetailContent(
    incident: Incident,
    isOwner: Boolean,
    hasUserConfirmed: Boolean,
    onConfirm: () -> Unit,
    onUnconfirm: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // ==========================================
        // MAPA
        // ==========================================

        val markerPosition = LatLng(
            incident.location.latitude,
            incident.location.longitude
        )

        val cameraPositionState = rememberCameraPositionState {
            position = CameraPosition.fromLatLngZoom(markerPosition, 15f)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        ) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = false,
                    scrollGesturesEnabled = false,
                    zoomGesturesEnabled = false
                )
            ) {
                Marker(
                    state = MarkerState(position = markerPosition),
                    title = incident.category
                )
            }
        }

        // ==========================================
        // INFORMACIÓN PRINCIPAL
        // ==========================================

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Tipo y verificación
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        incident.category,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        incident.type.name.lowercase().capitalize(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (incident.verified) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Verified,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "Verificado",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Divider()

            // Descripción
            DetailSection(
                icon = Icons.Filled.Description,
                title = "Descripción"
            ) {
                Text(
                    incident.description,
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            // Ubicación
            DetailSection(
                icon = Icons.Filled.LocationOn,
                title = "Ubicación"
            ) {
                Text(
                    incident.address.ifBlank { "Sin dirección específica" },
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "${incident.location.latitude}, ${incident.location.longitude}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Reportado por
            DetailSection(
                icon = Icons.Filled.Person,
                title = "Reportado por"
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        incident.userName,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (isOwner) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                "Tú",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
                Text(
                    formatTimestamp(incident.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Divider()

            // Confirmaciones comunitarias
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Filled.People,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Confirmaciones comunitarias",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${incident.confirmations} personas confirmaron este incidente",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        // Botón de confirmar/desconfirmar
                        if (hasUserConfirmed) {
                            OutlinedButton(onClick = onUnconfirm) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Quitar")
                            }
                        } else {
                            FilledTonalButton(
                                onClick = onConfirm,
                                enabled = !incident.verified && !isOwner
                            ) {
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Confirmar")
                            }
                        }
                    }

                    // Mensaje informativo
                    if (!incident.verified && incident.confirmations < 3) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Info,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    "Se necesitan ${3 - incident.confirmations} confirmaciones más para verificar",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }

            // TODO: Galería de fotos
            // if (incident.photos.isNotEmpty()) { ... }
        }
    }
}

@Composable
private fun DetailSection(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }
        content()
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
>>>>>>> Stashed changes
