package com.example.safecity.screens.dashboard

import android.Manifest
import android.annotation.SuppressLint
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.safecity.models.IncidentType
import com.example.safecity.viewmodel.DashboardViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.tasks.await
import com.google.accompanist.permissions.MultiplePermissionsState
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState



@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onLogout: () -> Unit,
    viewModel: DashboardViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    // ✅ PERMISOS DE UBICACIÓN
    val locationPermissions = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    // ✅ Estado del mapa
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            uiState.userLocation ?: LatLng(4.6097, -74.0817), // Bogotá default
            13f
        )
    }

    // ✅ Obtener ubicación cuando se concedan permisos
    LaunchedEffect(locationPermissions.allPermissionsGranted) {
        if (locationPermissions.allPermissionsGranted) {
            try {
                val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                @SuppressLint("MissingPermission")
                val location = fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    null
                ).await()

                location?.let {
                    viewModel.updateUserLocation(it)
                    cameraPositionState.move(
                        CameraUpdateFactory.newLatLngZoom(
                            LatLng(it.latitude, it.longitude),
                            15f
                        )
                    )
                }
            } catch (e: Exception) {
                // Error obteniendo ubicación
            }
        }
    }

    // ✅ Bottom Sheet
    val sheetState = rememberModalBottomSheetState()
    var showBottomSheet by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.selectedIncident) {
        showBottomSheet = uiState.selectedIncident != null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SafeCity Dashboard") },
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Filled.Logout, "Cerrar sesión")
                    }
                }
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Botón "Mi ubicación"
                if (uiState.userLocation != null) {
                    SmallFloatingActionButton(
                        onClick = {
                            uiState.userLocation?.let {
                                cameraPositionState.move(
                                    CameraUpdateFactory.newLatLngZoom(it, 15f)
                                )
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.surface,
                        shape = CircleShape
                    ) {
                        Icon(Icons.Filled.MyLocation, "Mi ubicación")
                    }
                }

                // FAB crear reporte
                FloatingActionButton(
                    onClick = { /* TODO: Navegar a CreateIncidentScreen */ }
                ) {
                    Icon(Icons.Filled.Add, "Nuevo reporte")
                }
            }
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {

            // ==========================================
            // VERIFICAR PERMISOS
            // ==========================================

            when {
                // ✅ PERMISOS CONCEDIDOS → Mostrar mapa
                locationPermissions.allPermissionsGranted -> {
                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraPositionState,
                        properties = MapProperties(
                            isMyLocationEnabled = true
                        ),
                        uiSettings = MapUiSettings(
                            zoomControlsEnabled = false,
                            myLocationButtonEnabled = false
                        )
                    ) {
                        // Marcadores de incidentes
                        uiState.filteredIncidents.forEach { incident ->
                            Marker(
                                state = MarkerState(
                                    position = LatLng(
                                        incident.location.latitude,
                                        incident.location.longitude
                                    )
                                ),
                                title = incident.category,
                                snippet = incident.description,
                                icon = BitmapDescriptorFactory.defaultMarker(
                                    if (incident.type == IncidentType.SEGURIDAD)
                                        BitmapDescriptorFactory.HUE_RED
                                    else
                                        BitmapDescriptorFactory.HUE_BLUE
                                ),
                                onClick = {
                                    viewModel.selectIncident(incident)
                                    true
                                }
                            )
                        }
                    }

                    // Filtros flotantes
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = uiState.filterType == IncidentType.SEGURIDAD,
                            onClick = {
                                viewModel.filterByType(
                                    if (uiState.filterType == IncidentType.SEGURIDAD) null
                                    else IncidentType.SEGURIDAD
                                )
                            },
                            label = { Text("🚨 Seguridad") },
                            leadingIcon = if (uiState.filterType == IncidentType.SEGURIDAD) {
                                { Icon(Icons.Filled.Check, null, Modifier.size(18.dp)) }
                            } else null
                        )

                        FilterChip(
                            selected = uiState.filterType == IncidentType.INFRAESTRUCTURA,
                            onClick = {
                                viewModel.filterByType(
                                    if (uiState.filterType == IncidentType.INFRAESTRUCTURA) null
                                    else IncidentType.INFRAESTRUCTURA
                                )
                            },
                            label = { Text("🏗️ Infraestructura") },
                            leadingIcon = if (uiState.filterType == IncidentType.INFRAESTRUCTURA) {
                                { Icon(Icons.Filled.Check, null, Modifier.size(18.dp)) }
                            } else null
                        )

                        FilterChip(
                            selected = uiState.showVerifiedOnly,
                            onClick = { viewModel.toggleVerifiedFilter() },
                            label = { Text("✅ Verificados") },
                            leadingIcon = if (uiState.showVerifiedOnly) {
                                { Icon(Icons.Filled.Check, null, Modifier.size(18.dp)) }
                            } else null
                        )
                    }
                }

                // ⏳ PERMISOS NO CONCEDIDOS → Mostrar pantalla de permisos
                else -> {
                    PermissionRequestScreen(
                        permissionsState = locationPermissions,
                        onRequestPermission = { locationPermissions.launchMultiplePermissionRequest() }
                    )
                }
            }

            // Loading overlay
            if (uiState.loading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }

            // Error snackbar
            uiState.error?.let { error ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    action = {
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text("OK")
                        }
                    }
                ) {
                    Text(error)
                }
            }
        }

        // BOTTOM SHEET
        if (showBottomSheet && uiState.selectedIncident != null) {
            ModalBottomSheet(
                onDismissRequest = {
                    showBottomSheet = false
                    viewModel.selectIncident(null)
                },
                sheetState = sheetState
            ) {
                uiState.selectedIncident?.let { selectedIncident ->
                    ModalBottomSheet(
                        onDismissRequest = { viewModel.selectIncident(null) },
                        sheetState = rememberModalBottomSheetState()
                    ) {
                        IncidentDetailsSheet(
                            incident = selectedIncident,
                            userLocation = uiState.userLocation,
                            onConfirm = { viewModel.confirmIncident(it) },
                            onUnconfirm = { viewModel.unconfirmIncident(it) },
                            hasUserConfirmed = viewModel.hasUserConfirmed(selectedIncident),
                            calculateDistance = viewModel::calculateDistance
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// PANTALLA DE SOLICITUD DE PERMISOS
// ==========================================

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionRequestScreen(
    permissionsState: MultiplePermissionsState,
    onRequestPermission: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(modifier = Modifier.padding(24.dp)) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    Icons.Filled.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Text(
                    "Permisos de ubicación necesarios",
                    style = MaterialTheme.typography.titleLarge
                )

                Text(
                    "SafeCity necesita acceso a tu ubicación para mostrarte incidentes cercanos y permitirte crear reportes en tu área.",
                    style = MaterialTheme.typography.bodyMedium
                )

                // (Opcional) mensaje si el sistema recomienda mostrar explicación
                if (permissionsState.shouldShowRationale) {
                    Text(
                        "Parece que rechazaste los permisos antes. Si los aceptas, podremos mostrar tu ubicación en el mapa.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Button(
                    onClick = onRequestPermission,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.LocationOn, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Conceder permisos")
                }
            }
        }
    }
}