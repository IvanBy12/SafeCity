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

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onLogout: () -> Unit,
    onNavigateToCreateIncident: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onNavigateToIncidentDetail: (String) -> Unit = {},
    viewModel: DashboardViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    val locationPermissions = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            uiState.userLocation ?: LatLng(4.6097, -74.0817), 13f
        )
    }

    LaunchedEffect(locationPermissions.allPermissionsGranted) {
        if (locationPermissions.allPermissionsGranted) {
            try {
                val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                @SuppressLint("MissingPermission")
                val location = fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
                location?.let {
                    viewModel.updateUserLocation(it)
                    cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 15f))
                }
            } catch (_: Exception) { }
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
                    IconButton(onClick = onNavigateToProfile) { Icon(Icons.Filled.Person, "Perfil") }
                    IconButton(onClick = onLogout) { Icon(Icons.Filled.Logout, "Cerrar sesión") }
                }
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (uiState.userLocation != null) {
                    SmallFloatingActionButton(
                        onClick = {
                            uiState.userLocation?.let { cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(it, 15f)) }
                        },
                        containerColor = MaterialTheme.colorScheme.surface,
                        shape = CircleShape
                    ) { Icon(Icons.Filled.MyLocation, "Mi ubicación") }
                }
                FloatingActionButton(onClick = onNavigateToCreateIncident) { Icon(Icons.Filled.Add, "Nuevo reporte") }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                locationPermissions.allPermissionsGranted -> {
                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraPositionState,
                        properties = MapProperties(isMyLocationEnabled = true),
                        uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false)
                    ) {
                        uiState.filteredIncidents.forEach { incident ->
                            Marker(
                                state = MarkerState(position = LatLng(incident.location.latitude, incident.location.longitude)),
                                title = incident.category,
                                snippet = incident.description,
                                icon = BitmapDescriptorFactory.defaultMarker(
                                    if (incident.type == IncidentType.SEGURIDAD) BitmapDescriptorFactory.HUE_RED
                                    else BitmapDescriptorFactory.HUE_BLUE
                                ),
                                onClick = { viewModel.selectIncident(incident); true }
                            )
                        }
                    }

                    // Filtros dropdown
                    Box(modifier = Modifier.align(Alignment.TopStart).padding(12.dp)) {
                        ElevatedFilterChip(
                            selected = hasActiveFilters,
                            onClick = { filtersExpanded = !filtersExpanded },
                            label = { Text(activeFilterLabel) },
                            leadingIcon = { Icon(if (hasActiveFilters) Icons.Filled.FilterAlt else Icons.Filled.FilterList, null, Modifier.size(18.dp)) },
                            trailingIcon = { Icon(if (filtersExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, Modifier.size(18.dp)) }
                        )

                        DropdownMenu(expanded = filtersExpanded, onDismissRequest = { filtersExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("Seguridad") },
                                onClick = { viewModel.filterByType(if (uiState.filterType == IncidentType.SEGURIDAD) null else IncidentType.SEGURIDAD) },
                                leadingIcon = { Checkbox(checked = uiState.filterType == IncidentType.SEGURIDAD, onCheckedChange = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Infraestructura") },
                                onClick = { viewModel.filterByType(if (uiState.filterType == IncidentType.INFRAESTRUCTURA) null else IncidentType.INFRAESTRUCTURA) },
                                leadingIcon = { Checkbox(checked = uiState.filterType == IncidentType.INFRAESTRUCTURA, onCheckedChange = null) }
                            )
                            Divider(Modifier.padding(vertical = 4.dp))
                            DropdownMenuItem(
                                text = { Text("Solo verificados") },
                                onClick = { viewModel.toggleVerifiedFilter() },
                                leadingIcon = { Checkbox(checked = uiState.showVerifiedOnly, onCheckedChange = null) }
                            )
                            if (hasActiveFilters) {
                                Divider(Modifier.padding(vertical = 4.dp))
                                DropdownMenuItem(
                                    text = { Text("Limpiar filtros", color = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        viewModel.filterByType(null)
                                        if (uiState.showVerifiedOnly) viewModel.toggleVerifiedFilter()
                                        filtersExpanded = false
                                    },
                                    leadingIcon = { Icon(Icons.Filled.Clear, null, tint = MaterialTheme.colorScheme.error) }
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

            if (uiState.loading) { CircularProgressIndicator(Modifier.align(Alignment.Center)) }

            uiState.error?.let { error ->
                Snackbar(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    action = { TextButton(onClick = { viewModel.clearError() }) { Text("OK") } }
                ) { Text(error) }
            }
        }

        // ========================================
        // BOTTOM SHEET CON VOTOS + COMENTARIOS
        // ========================================
        if (showBottomSheet && uiState.selectedIncident != null) {
            ModalBottomSheet(
                onDismissRequest = { showBottomSheet = false; viewModel.selectIncident(null) },
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
                        // ✅ COMENTARIOS CONECTADOS
                        comments = uiState.comments,
                        commentsLoading = uiState.commentsLoading,
                        commentSending = uiState.commentSending,
                        onSendComment = { text -> viewModel.sendComment(selectedIncident.id, text) },
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
                Icon(Icons.Filled.LocationOn, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                Text("Permisos de ubicación necesarios", style = MaterialTheme.typography.titleLarge)
                Text("SafeCity necesita acceso a tu ubicación para mostrarte incidentes cercanos.", style = MaterialTheme.typography.bodyMedium)
                if (permissionsState.shouldShowRationale) {
                    Text("Parece que rechazaste los permisos antes.", style = MaterialTheme.typography.bodySmall)
                }
                Button(onClick = onRequestPermission, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.LocationOn, null); Spacer(Modifier.width(8.dp)); Text("Conceder permisos")
                }
            }
        }
    }
}