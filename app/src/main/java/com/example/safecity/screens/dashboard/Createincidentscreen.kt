package com.example.safecity.screens.dashboard

import android.annotation.SuppressLint
import android.location.Geocoder
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.safecity.models.Incident
import com.example.safecity.models.IncidentCategories
import com.example.safecity.models.IncidentType
import com.example.safecity.viewmodel.DashboardViewModel
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.firestore.GeoPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateIncidentScreen(
    onBack: () -> Unit,
    viewModel: DashboardViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedType by remember { mutableStateOf(IncidentType.SEGURIDAD) }
    var selectedCategory by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var currentLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var address by remember { mutableStateOf("Obteniendo ubicación...") }
    var loading by remember { mutableStateOf(false) }

    val categories = when (selectedType) {
        IncidentType.SEGURIDAD -> IncidentCategories.SEGURIDAD
        IncidentType.INFRAESTRUCTURA -> IncidentCategories.INFRAESTRUCTURA
    }

    // ✅ Obtener ubicación al abrir
    LaunchedEffect(Unit) {
        try {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            @SuppressLint("MissingPermission")
            val location = fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                null
            ).await()

            location?.let {
                currentLocation = GeoPoint(it.latitude, it.longitude)

                // Geocoding inverso para obtener dirección
                try {
                    val geocoder = Geocoder(context, Locale.getDefault())
                    val addresses = geocoder.getFromLocation(it.latitude, it.longitude, 1)
                    address = addresses?.firstOrNull()?.getAddressLine(0) ?: "Ubicación desconocida"
                } catch (e: Exception) {
                    address = "Lat: ${it.latitude}, Lng: ${it.longitude}"
                }
            }
        } catch (e: Exception) {
            address = "Error obteniendo ubicación"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nuevo Reporte") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ✅ Selector de tipo
            Text("Tipo de incidente", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selectedType == IncidentType.SEGURIDAD,
                    onClick = {
                        selectedType = IncidentType.SEGURIDAD
                        selectedCategory = ""
                    },
                    label = { Text("🚨 Seguridad") }
                )
                FilterChip(
                    selected = selectedType == IncidentType.INFRAESTRUCTURA,
                    onClick = {
                        selectedType = IncidentType.INFRAESTRUCTURA
                        selectedCategory = ""
                    },
                    label = { Text("🏗️ Infraestructura") }
                )
            }

            // ✅ Selector de categoría
            Text("Categoría", style = MaterialTheme.typography.titleMedium)
            categories.chunked(3).forEach { rowCategories ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowCategories.forEach { category ->
                        FilterChip(
                            selected = selectedCategory == category,
                            onClick = { selectedCategory = category },
                            label = { Text(category) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // Rellenar espacios vacíos
                    repeat(3 - rowCategories.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }

            // ✅ Descripción
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Descripción (opcional)") },
                placeholder = { Text("Describe lo que sucedió...") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5
            )

            // ✅ Ubicación
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Ubicación", style = MaterialTheme.typography.titleMedium)
                    Text(
                        address,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ✅ Botón crear
            Button(
                onClick = {
                    if (selectedCategory.isBlank()) {
                        // TODO: Mostrar error
                        return@Button
                    }
                    if (currentLocation == null) {
                        // TODO: Mostrar error
                        return@Button
                    }

                    val incident = Incident(
                        type = selectedType,
                        category = selectedCategory,
                        description = description.trim(),
                        location = currentLocation!!,
                        address = address
                    )

                    loading = true
                    viewModel.createIncident(incident) {
                        loading = false
                        onBack()
                    }
                },
                enabled = !loading && selectedCategory.isNotBlank() && currentLocation != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (loading) "Creando..." else "Crear Reporte")
            }
        }
    }
}