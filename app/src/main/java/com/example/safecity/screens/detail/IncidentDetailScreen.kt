package com.example.safecity.screens.detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.safecity.models.Comment
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
    var commentText by remember { mutableStateOf("") }

    LaunchedEffect(incidentId) {
        viewModel.loadIncident(incidentId)
        viewModel.loadComments(incidentId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detalle del Incidente") },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.Filled.ArrowBack, "Volver") } },
                actions = {
                    if (uiState.isOwner) {
                        IconButton(onClick = { showDeleteDialog = true }) { Icon(Icons.Filled.Delete, "Eliminar") }
                    }
                }
            )
        },
        // Input de comentario fijo al fondo
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = commentText,
                        onValueChange = { commentText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Agrega un comentario público") },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        ),
                        textStyle = MaterialTheme.typography.bodyMedium,
                        enabled = !uiState.commentSending
                    )

                    IconButton(
                        onClick = {
                            if (commentText.isNotBlank()) {
                                viewModel.sendComment(incidentId, commentText.trim())
                                commentText = ""
                            }
                        },
                        enabled = commentText.isNotBlank() && !uiState.commentSending
                    ) {
                        if (uiState.commentSending) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                Icons.Filled.Send,
                                contentDescription = "Enviar comentario",
                                tint = if (commentText.isNotBlank())
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                uiState.error != null -> {
                    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Filled.ErrorOutline, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(16.dp))
                        Text("Error cargando incidente", style = MaterialTheme.typography.titleMedium)
                        Text(uiState.error ?: "", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                uiState.incident != null -> {
                    IncidentDetailContent(
                        incident = uiState.incident!!,
                        isOwner = uiState.isOwner,
                        userVoteStatus = uiState.userVoteStatus,
                        onVoteTrue = { viewModel.voteTrue() },
                        onVoteFalse = { viewModel.voteFalse() },
                        onRemoveVote = { viewModel.removeVote() },
                        comments = uiState.comments,
                        commentsLoading = uiState.commentsLoading
                    )
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = { Icon(Icons.Filled.Warning, null) },
            title = { Text("Eliminar reporte") },
            text = { Text("¿Estás seguro? Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(
                    onClick = { showDeleteDialog = false; viewModel.deleteIncident(); onNavigateBack() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun IncidentDetailContent(
    incident: Incident,
    isOwner: Boolean,
    userVoteStatus: String,
    onVoteTrue: () -> Unit,
    onVoteFalse: () -> Unit,
    onRemoveVote: () -> Unit,
    comments: List<Comment>,
    commentsLoading: Boolean
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {

        // FOTO
        val photoUrl = incident.firstPhoto
        if (!photoUrl.isNullOrBlank()) {
            AsyncImage(
                model = photoUrl,
                contentDescription = "Foto del incidente",
                modifier = Modifier.fillMaxWidth().height(240.dp),
                contentScale = ContentScale.Crop
            )
        }

        // MAPA
        val markerPosition = LatLng(incident.location.latitude, incident.location.longitude)
        val cameraPositionState = rememberCameraPositionState {
            position = CameraPosition.fromLatLngZoom(markerPosition, 15f)
        }

        Box(Modifier.fillMaxWidth().height(180.dp)) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                uiSettings = MapUiSettings(zoomControlsEnabled = false, scrollGesturesEnabled = false, zoomGesturesEnabled = false)
            ) { Marker(state = MarkerState(position = markerPosition), title = incident.category) }
        }

        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Header
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(incident.category, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(incident.type.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }

                when {
                    incident.flaggedFalse -> {
                        Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Warning, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
                                Text("Falso", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    incident.verified -> {
                        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.medium) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Verified, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                Text("Verificado", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            if (incident.description.isNotBlank()) {
                DetailSection(Icons.Filled.Description, "Descripción") {
                    Text(incident.description, style = MaterialTheme.typography.bodyLarge)
                }
            }

            DetailSection(Icons.Filled.LocationOn, "Ubicación") {
                Text(incident.address.ifBlank { "Sin dirección" }, style = MaterialTheme.typography.bodyMedium)
            }

            DetailSection(Icons.Filled.AccessTime, "Fecha") {
                Text(formatTimestamp(incident.timestamp), style = MaterialTheme.typography.bodyMedium)
            }

            if (incident.photos.size > 1) {
                DetailSection(Icons.Filled.Photo, "Fotos") {
                    Text("${incident.photos.size} fotos adjuntas", style = MaterialTheme.typography.bodyMedium)
                }
            }

            HorizontalDivider()

            // ========================================
            // VALIDACIÓN COMUNITARIA
            // ========================================
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.HowToVote, null, tint = MaterialTheme.colorScheme.primary)
                        Text("Validación comunitaria", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.ThumbUp, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(28.dp))
                            Text("${incident.votedTrueCount}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                            Text("Confirman", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val scoreColor = when {
                                incident.validationScore >= 3 -> Color(0xFF4CAF50)
                                incident.validationScore <= -3 -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                            Text(
                                "${if (incident.validationScore > 0) "+" else ""}${incident.validationScore}",
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold,
                                color = scoreColor
                            )
                            Text("Score", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.ThumbDown, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(28.dp))
                            Text("${incident.votedFalseCount}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                            Text("Niegan", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    val totalVotes = incident.votedTrueCount + incident.votedFalseCount
                    if (totalVotes > 0) {
                        LinearProgressIndicator(
                            progress = { incident.votedTrueCount.toFloat() / totalVotes.toFloat() },
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFF4CAF50),
                            trackColor = MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                        )
                    }

                    HorizontalDivider()

                    if (isOwner) {
                        Text("No puedes votar en tu propio reporte", Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    } else {
                        when (userVoteStatus) {
                            "none" -> {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilledTonalButton(onClick = onVoteTrue, modifier = Modifier.weight(1f), colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF4CAF50).copy(alpha = 0.15f))) {
                                        Icon(Icons.Filled.ThumbUp, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Confirmar")
                                    }
                                    FilledTonalButton(onClick = onVoteFalse, modifier = Modifier.weight(1f), colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.12f))) {
                                        Icon(Icons.Filled.ThumbDown, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Es falso")
                                    }
                                }
                            }
                            "true" -> {
                                Surface(color = Color(0xFF4CAF50).copy(alpha = 0.08f), shape = MaterialTheme.shapes.small) {
                                    Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.Check, null, Modifier.size(18.dp), tint = Color(0xFF4CAF50))
                                        Text("Confirmaste este reporte", style = MaterialTheme.typography.bodySmall, color = Color(0xFF388E3C), modifier = Modifier.weight(1f))
                                        TextButton(onClick = onRemoveVote) { Text("Quitar voto", style = MaterialTheme.typography.labelSmall) }
                                    }
                                }
                            }
                            "false" -> {
                                Surface(color = MaterialTheme.colorScheme.error.copy(alpha = 0.08f), shape = MaterialTheme.shapes.small) {
                                    Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.Close, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                                        Text("Reportaste como falso", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                                        TextButton(onClick = onRemoveVote) { Text("Quitar voto", style = MaterialTheme.typography.labelSmall) }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            // ========================================
            // COMENTARIOS
            // ========================================
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Forum, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text("Comentarios (${comments.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            if (commentsLoading) {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            } else if (comments.isEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        "No hay comentarios aún. ¡Sé el primero!",
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    comments.forEach { comment ->
                        CommentItemDetail(comment = comment)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun CommentItemDetail(comment: Comment) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            modifier = Modifier.size(36.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Person, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(comment.displayName, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(comment.timeAgo(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(comment.text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun DetailSection(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
        content()
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}