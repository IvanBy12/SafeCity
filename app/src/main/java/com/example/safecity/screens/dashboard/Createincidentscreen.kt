package com.example.safecity.screens.dashboard

import android.Manifest
import android.annotation.SuppressLint
import android.location.Geocoder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.example.safecity.models.Incident
import com.example.safecity.models.IncidentCategories
import com.example.safecity.models.IncidentType
import com.example.safecity.repository.StorageRepository
import com.example.safecity.viewmodel.DashboardViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.firestore.GeoPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun CreateIncidentScreen(
    onBack: () -> Unit,
    // ─── CAMBIO CLAVE ────────────────────────────────────────────────────────
    // Mismo factory que en DashboardScreen para reutilizar la misma instancia
    // del ViewModel (Compose los comparte dentro del mismo NavBackStackEntry scope).
    // ─────────────────────────────────────────────────────────────────────────
    viewModel: DashboardViewModel = viewModel(factory = DashboardViewModel.Factory)
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedType by remember { mutableStateOf(IncidentType.SEGURIDAD) }
    var selectedCategory by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var currentLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var address by remember { mutableStateOf("Obteniendo ubicación...") }
    var loading by remember { mutableStateOf(false) }
    var uploadingPhoto by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }
    var showPhotoOptions by remember { mutableStateOf(false) }

    val storageRepository = remember { StorageRepository() }
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success -> if (success) photoUri = tempCameraUri }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { photoUri = it } }

    fun createTempImageUri(): Uri {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFile = File(context.cacheDir, "JPEG_${timeStamp}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.provider", imageFile)
    }

    fun launchCamera() {
        if (cameraPermission.status.isGranted) {
            val uri = createTempImageUri()
            tempCameraUri = uri
            cameraLauncher.launch(uri)
        } else {
            cameraPermission.launchPermissionRequest()
        }
    }

    val categories = when (selectedType) {
        IncidentType.SEGURIDAD -> IncidentCategories.SEGURIDAD
        IncidentType.INFRAESTRUCTURA -> IncidentCategories.INFRAESTRUCTURA
    }

    LaunchedEffect(Unit) {
        try {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            @SuppressLint("MissingPermission")
            val location = fusedLocationClient
                .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .await()
            location?.let {
                currentLocation = GeoPoint(it.latitude, it.longitude)
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
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Volver") }
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
            // TIPO
            Text("Tipo de incidente", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selectedType == IncidentType.SEGURIDAD,
                    onClick = { selectedType = IncidentType.SEGURIDAD; selectedCategory = "" },
                    label = { Text("🚨 Seguridad") }
                )
                FilterChip(
                    selected = selectedType == IncidentType.INFRAESTRUCTURA,
                    onClick = { selectedType = IncidentType.INFRAESTRUCTURA; selectedCategory = "" },
                    label = { Text("🏗️ Infraestructura") }
                )
            }

            // CATEGORÍA
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
                    repeat(3 - rowCategories.size) { Spacer(Modifier.weight(1f)) }
                }
            }

            // DESCRIPCIÓN
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Descripción (opcional)") },
                placeholder = { Text("Describe lo que sucedió...") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5
            )

            // FOTO
            Text("Foto (opcional)", style = MaterialTheme.typography.titleMedium)
            if (photoUri != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    Image(
                        painter = rememberAsyncImagePainter(photoUri),
                        contentDescription = "Foto del incidente",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    IconButton(
                        onClick = { photoUri = null },
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Icon(
                                Icons.Filled.Close, "Quitar foto",
                                modifier = Modifier.padding(6.dp),
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                    TextButton(
                        onClick = { showPhotoOptions = true },
                        modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                        ) {
                            Text("Cambiar foto", modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                        }
                    }
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedCard(
                        modifier = Modifier.weight(1f).height(100.dp).clickable { launchCamera() }
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Filled.CameraAlt, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(4.dp))
                            Text("Tomar foto", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    OutlinedCard(
                        modifier = Modifier.weight(1f).height(100.dp).clickable { galleryLauncher.launch("image/*") }
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Filled.PhotoLibrary, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(4.dp))
                            Text("Galería", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            // UBICACIÓN
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.LocationOn, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Text("Ubicación", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(address, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            errorMsg?.let { msg ->
                Text(msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            Button(
                onClick = {
                    errorMsg = null
                    if (selectedCategory.isBlank()) { errorMsg = "Selecciona una categoría"; return@Button }
                    if (currentLocation == null) { errorMsg = "Esperando ubicación..."; return@Button }

                    val incident = Incident(
                        type = selectedType,
                        category = selectedCategory,
                        description = description.trim(),
                        location = currentLocation!!,
                        address = address
                    )
                    loading = true

                    if (photoUri != null) {
                        uploadingPhoto = true
                        scope.launch {
                            storageRepository.uploadIncidentPhoto(context, photoUri!!)
                                .onSuccess { downloadUrl ->
                                    uploadingPhoto = false
                                    viewModel.createIncident(incident, listOf(downloadUrl)) {
                                        loading = false
                                        onBack()
                                    }
                                }
                                .onFailure { e ->
                                    uploadingPhoto = false
                                    loading = false
                                    errorMsg = "Error subiendo foto: ${e.message}"
                                }
                        }
                    } else {
                        viewModel.createIncident(incident, emptyList()) {
                            loading = false
                            onBack()
                        }
                    }
                },
                enabled = !loading && !uploadingPhoto && selectedCategory.isNotBlank() && currentLocation != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                when {
                    uploadingPhoto -> {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Subiendo foto...")
                    }
                    loading -> {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Creando reporte...")
                    }
                    else -> Text("Crear Reporte")
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    if (showPhotoOptions) {
        AlertDialog(
            onDismissRequest = { showPhotoOptions = false },
            icon = { Icon(Icons.Filled.Photo, null) },
            title = { Text("Cambiar foto") },
            text = { Text("¿Cómo quieres agregar la foto?") },
            confirmButton = {
                TextButton(onClick = { showPhotoOptions = false; launchCamera() }) {
                    Icon(Icons.Filled.CameraAlt, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Cámara")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPhotoOptions = false; galleryLauncher.launch("image/*") }) {
                    Icon(Icons.Filled.PhotoLibrary, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Galería")
                }
            }
        )
    }
}