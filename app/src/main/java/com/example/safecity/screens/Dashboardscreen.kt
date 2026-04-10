package com.example.safecity.screens.dashboard

import android.Manifest
import android.annotation.SuppressLint
import android.os.Build
import android.os.Looper
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
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onLogout: () -> Unit,
    onNavigateToCreateIncident: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onNavigateToIncidentDetail: (String) -> Unit = {},
    onNavigateToReports: () -> Unit = {},
    // ─── CAMBIO CLAVE ────────────────────────────────────────────────────────
    // Se pasa DashboardViewModel.Factory para que Compose pueda construir
    // correctamente un AndroidViewModel que necesita Application en el constructor.
    // Sin el factory la app crashea con "Cannot create instance of DashboardViewModel".
    // ─────────────────────────────────────────────────────────────────────────
    viewModel: DashboardViewModel = viewModel(factory = DashboardViewModel.Factory)
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    // ─── Permisos requeridos ──────────────────────────────────────────────
    // POST_NOTIFICATIONS es un permiso de runtime a partir de Android 13 (API 33).
    // Accompanist lo trata como "concedido" automáticamente en versiones anteriores.
    val requiredPermissions = remember {
        buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val locationPermissions = rememberMultiplePermissionsState(
        permissions = requiredPermissions
    )

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            uiState.userLocation ?: LatLng(4.6097, -74.0817), 13f
        )
    }

    val locationGranted = locationPermissions.permissions
        .filter { it.permission == Manifest.permission.ACCESS_FINE_LOCATION ||
                  it.permission == Manifest.permission.ACCESS_COARSE_LOCATION }
        .any { it.status.isGranted }

    // ─── Ubicación inicial: mueve la cámara la primera vez ───────────────
    LaunchedEffect(locationGranted) {
        if (locationGranted) {
            try {
                val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                @SuppressLint("MissingPermission")
                val location = fusedLocationClient
                    .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .await()
                location?.let {
                    viewModel.updateUserLocation(it)
                    cameraPositionState.move(
                        CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 15f)
                    )
                }
            } catch (_: Exception) { }
        }
    }

    // ─── Actualización continua de ubicación ─────────────────────────────
    // Se suscribe mientras el Dashboard está visible y limpia al salir.
    // Intervalo: cada 60 s o cuando el usuario se mueva al menos 50 m.
    // Esto garantiza que lastLocation en el backend esté siempre actualizada
    // para la consulta geoespacial de notificaciones FCM a 500 m.
    DisposableEffect(locationGranted) {
        if (!locationGranted) return@DisposableEffect onDispose {}

        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            60_000L          // intervalo nominal: 60 s
        )
            .setMinUpdateDistanceMeters(50f)   // o 50 m de desplazamiento
            .setMinUpdateIntervalMillis(30_000L) // no más frecuente que 30 s
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { viewModel.updateUserLocation(it) }
            }
        }

        @SuppressLint("MissingPermission")
        val locationUpdateTask = fusedLocationClient.requestLocationUpdates(
            locationRequest,
            callback,
            Looper.getMainLooper()
        )
        // locationUpdateTask no se usa directamente; requestLocationUpdates es fire-and-forget.
        // La limpieza se hace en onDispose con removeLocationUpdates.

        onDispose {
            fusedLocationClient.removeLocationUpdates(callback)
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showBottomSheet by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.selectedIncident) {
        showBottomSheet = uiState.selectedIncident != null
    }

    var filtersExpanded by remember { mutableStateOf(false) }

    val activeFilterLabel = remember(uiState.filterType, uiState.showVerifiedOnly) {
        val parts = mutableListOf<String>()
        when (uiState.filterType) {
            IncidentType.SEGURIDAD -> parts.add("Seguridad")
            IncidentType.INFRAESTRUCTURA -> parts.add("Infraestructura")
            null -> {}
        }
        if (uiState.showVerifiedOnly) parts.add("Verificados")
        if (parts.isEmpty()) "Filtros" else parts.joinToString(", ")
    }

    val hasActiveFilters = uiState.filterType != null || uiState.showVerifiedOnly

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SafeCity Dashboard") },
                actions = {
                    IconButton(onClick = onNavigateToReports) {
                        Icon(Icons.Filled.Assessment, "Reportes")
                    }
                    IconButton(onClick = onNavigateToProfile) {
                        Icon(Icons.Filled.Person, "Perfil")
                    }
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
                if (uiState.userLocation != null) {
                    SmallFloatingActionButton(
                        onClick = {
                            uiState.userLocation?.let {
                                cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(it, 15f))
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.surface,
                        shape = CircleShape
                    ) {
                        Icon(Icons.Filled.MyLocation, "Mi ubicación")
                    }
                }
                FloatingActionButton(onClick = onNavigateToCreateIncident) {
                    Icon(Icons.Filled.Add, "Nuevo reporte")
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                locationGranted -> {
                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraPositionState,
                        properties = MapProperties(isMyLocationEnabled = locationGranted),
                        uiSettings = MapUiSettings(
                            zoomControlsEnabled = false,
                            myLocationButtonEnabled = false
                        )
                    ) {
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
                                onClick = { viewModel.selectIncident(incident); true }
                            )
                        }
                    }

                    // Filtros
                    Box(modifier = Modifier.align(Alignment.TopStart).padding(12.dp)) {
                        ElevatedFilterChip(
                            selected = hasActiveFilters,
                            onClick = { filtersExpanded = !filtersExpanded },
                            label = { Text(activeFilterLabel) },
                            leadingIcon = {
                                Icon(
                                    if (hasActiveFilters) Icons.Filled.FilterAlt else Icons.Filled.FilterList,
                                    null, Modifier.size(18.dp)
                                )
                            },
                            trailingIcon = {
                                Icon(
                                    if (filtersExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                    null, Modifier.size(18.dp)
                                )
                            }
                        )

                        DropdownMenu(
                            expanded = filtersExpanded,
                            onDismissRequest = { filtersExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Seguridad") },
                                onClick = {
                                    viewModel.filterByType(
                                        if (uiState.filterType == IncidentType.SEGURIDAD) null
                                        else IncidentType.SEGURIDAD
                                    )
                                },
                                leadingIcon = {
                                    Checkbox(
                                        checked = uiState.filterType == IncidentType.SEGURIDAD,
                                        onCheckedChange = null
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Infraestructura") },
                                onClick = {
                                    viewModel.filterByType(
                                        if (uiState.filterType == IncidentType.INFRAESTRUCTURA) null
                                        else IncidentType.INFRAESTRUCTURA
                                    )
                                },
                                leadingIcon = {
                                    Checkbox(
                                        checked = uiState.filterType == IncidentType.INFRAESTRUCTURA,
                                        onCheckedChange = null
                                    )
                                }
                            )
                            Divider(Modifier.padding(vertical = 4.dp))
                            DropdownMenuItem(
                                text = { Text("Solo verificados") },
                                onClick = { viewModel.toggleVerifiedFilter() },
                                leadingIcon = {
                                    Checkbox(
                                        checked = uiState.showVerifiedOnly,
                                        onCheckedChange = null
                                    )
                                }
                            )
                            if (hasActiveFilters) {
                                Divider(Modifier.padding(vertical = 4.dp))
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "Limpiar filtros",
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    onClick = {
                                        viewModel.filterByType(null)
                                        if (uiState.showVerifiedOnly) viewModel.toggleVerifiedFilter()
                                        filtersExpanded = false
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Filled.Clear, null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
                else -> {
                    PermissionRequestScreen(
                        permissionsState = locationPermissions,
                        onRequestPermission = { locationPermissions.launchMultiplePermissionRequest() }
                    )
                }

            }

            if (uiState.loading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }

            uiState.error?.let { error ->
                Snackbar(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    action = { TextButton(onClick = { viewModel.clearError() }) { Text("OK") } }
                ) { Text(error) }
            }
        }

        // ==========================================
        // BOTTOM SHEET
        // ==========================================
        if (showBottomSheet && uiState.selectedIncident != null) {
            ModalBottomSheet(
                onDismissRequest = {
                    showBottomSheet = false
                    viewModel.selectIncident(null)
                },
                sheetState = sheetState
            ) {
                uiState.selectedIncident?.let { selectedIncident ->
                    IncidentDetailsSheet(
                        incident = selectedIncident,
                        userLocation = uiState.userLocation,
                        onVoteTrue = { viewModel.voteTrue(it) },
                        onVoteFalse = { viewModel.voteFalse(it) },
                        onRemoveVote = { viewModel.removeVote(it) },
                        isOwner = viewModel.isOwner(selectedIncident),
                        userVoteStatus = viewModel.getUserVoteStatus(selectedIncident),
                        calculateDistance = viewModel::calculateDistance,
                        comments = uiState.comments,
                        commentsLoading = uiState.commentsLoading,
                        commentSending = uiState.commentSending,
                        onSendComment = { text ->
                            viewModel.sendComment(
                                selectedIncident.id,
                                text
                            )
                        },
                        onLoadComments = { viewModel.loadComments(selectedIncident.id) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionRequestScreen(
    permissionsState: MultiplePermissionsState,
    onRequestPermission: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(modifier = Modifier.padding(24.dp)) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    Icons.Filled.LocationOn, null,
                    Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("Permisos de ubicación necesarios", style = MaterialTheme.typography.titleLarge)
                Text(
                    "SafeCity necesita acceso a tu ubicación para mostrarte incidentes cercanos.",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (permissionsState.shouldShowRationale) {
                    Text("Parece que rechazaste los permisos antes.", style = MaterialTheme.typography.bodySmall)
                }
                Button(onClick = onRequestPermission, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.LocationOn, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Conceder permisos")
                }
            }
        }
    }
}