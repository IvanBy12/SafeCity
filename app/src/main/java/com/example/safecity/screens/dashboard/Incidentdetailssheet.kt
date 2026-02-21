package com.example.safecity.screens.dashboard

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.safecity.models.Comment
import com.example.safecity.models.Incident
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.firestore.GeoPoint
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun IncidentDetailsSheet(
    incident: Incident,
    userLocation: LatLng?,
    onVoteTrue: (String) -> Unit,
    onVoteFalse: (String) -> Unit,
    onRemoveVote: (String) -> Unit,
    isOwner: Boolean,
    userVoteStatus: String,
    calculateDistance: (LatLng, GeoPoint) -> String,
    comments: List<Comment> = emptyList(),
    commentsLoading: Boolean = false,
    commentSending: Boolean = false,
    onSendComment: (String) -> Unit = {},
    onLoadComments: () -> Unit = {}
) {
    var commentText by remember { mutableStateOf("") }
    var showComments by remember { mutableStateOf(true) }

    // Carga comentarios automáticamente cuando se abre el sheet
    LaunchedEffect(incident.id) {
        onLoadComments()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        // ==========================================
        // CONTENIDO SCROLLABLE
        // BUG FIX: se agrega verticalScroll para que los comentarios
        // (que están al final) sean visibles y accesibles.
        // ==========================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()) // ← FIX PRINCIPAL
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // FOTO
            val photoUrl = incident.firstPhoto
            if (!photoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = "Foto del incidente",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            }

            // HEADER
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        incident.category,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        incident.type.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                when {
                    incident.flaggedFalse -> {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Row(
                                Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Filled.Warning, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                                Text("Falso", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    incident.verified -> {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Row(
                                Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Filled.Verified, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Text("Verificado", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            // META: tiempo + contadores
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "hace ${formatTimeAgo(incident.timestamp)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val commentCount = if (comments.isNotEmpty()) comments.size else incident.commentsCount
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("$commentCount", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Icon(Icons.Filled.ChatBubble, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${incident.votedTrueCount}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Icon(Icons.Filled.ThumbUp, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            HorizontalDivider()

            // DESCRIPCIÓN
            if (incident.description.isNotBlank()) {
                Text(incident.description, style = MaterialTheme.typography.bodyMedium)
            }

            // UBICACIÓN Y DISTANCIA
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Filled.LocationOn, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("Ubicación", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(incident.address.ifBlank { "Sin dirección" }, style = MaterialTheme.typography.bodySmall)
                }
                if (userLocation != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Filled.NearMe, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Text("Distancia", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(calculateDistance(userLocation, incident.location), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                }
            }

            HorizontalDivider()

            // VALIDACIÓN
            ValidationScoreSection(
                incident = incident,
                isOwner = isOwner,
                userVoteStatus = userVoteStatus,
                onVoteTrue = onVoteTrue,
                onVoteFalse = onVoteFalse,
                onRemoveVote = onRemoveVote
            )

            HorizontalDivider()

            // ==========================================
            // COMENTARIOS
            // ==========================================
            CommentsSection(
                comments = comments,
                commentsLoading = commentsLoading,
                showComments = showComments,
                onToggleComments = {
                    showComments = !showComments
                    if (showComments && comments.isEmpty()) onLoadComments()
                }
            )

            // Espacio al final para que el último comentario no quede
            // pegado al CommentInputBar
            Spacer(Modifier.height(8.dp))
        }

        // ==========================================
        // INPUT FIJO AL FONDO (fuera del scroll)
        // ==========================================
        CommentInputBar(
            commentText = commentText,
            onTextChange = { commentText = it },
            sending = commentSending,
            onSend = {
                if (commentText.isNotBlank()) {
                    onSendComment(commentText.trim())
                    commentText = ""
                    showComments = true
                }
            }
        )
    }
}

// ==========================================
// SECCIÓN DE COMENTARIOS
// ==========================================
@Composable
private fun CommentsSection(
    comments: List<Comment>,
    commentsLoading: Boolean,
    showComments: Boolean,
    onToggleComments: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Forum, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text(
                    "Comentarios (${comments.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            TextButton(onClick = onToggleComments) {
                Icon(if (showComments) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, Modifier.size(20.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (showComments) "Ocultar" else "Ver")
            }
        }

        if (showComments) {
            when {
                commentsLoading -> {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
                comments.isEmpty() -> {
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
                }
                else -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        comments.forEach { comment -> CommentItem(comment = comment) }
                    }
                }
            }
        }
    }
}

// ==========================================
// ITEM DE COMENTARIO
// ==========================================
@Composable
private fun CommentItem(comment: Comment) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(modifier = Modifier.size(32.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Person, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(comment.displayName, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(comment.timeAgo(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(comment.text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

// ==========================================
// BARRA DE INPUT
// ==========================================
@Composable
private fun CommentInputBar(
    commentText: String,
    onTextChange: (String) -> Unit,
    sending: Boolean,
    onSend: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = commentText,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Agrega un comentario público") },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                textStyle = MaterialTheme.typography.bodyMedium,
                enabled = !sending
            )
            IconButton(onClick = onSend, enabled = commentText.isNotBlank() && !sending) {
                if (sending) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        Icons.Filled.Send,
                        contentDescription = "Enviar",
                        tint = if (commentText.isNotBlank()) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}

// ==========================================
// VALIDACIÓN COMUNITARIA
// ==========================================
@Composable
private fun ValidationScoreSection(
    incident: Incident,
    isOwner: Boolean,
    userVoteStatus: String,
    onVoteTrue: (String) -> Unit,
    onVoteFalse: (String) -> Unit,
    onRemoveVote: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.HowToVote, null, tint = MaterialTheme.colorScheme.primary)
                Text("Validación comunitaria", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.ThumbUp, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(24.dp))
                    Text("${incident.votedTrueCount}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
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
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = scoreColor
                    )
                    Text("Score", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.ThumbDown, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(24.dp))
                    Text("${incident.votedFalseCount}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
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

            when {
                incident.verified -> {
                    Surface(color = Color(0xFF4CAF50).copy(alpha = 0.12f), shape = MaterialTheme.shapes.small) {
                        Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.Verified, null, Modifier.size(18.dp), tint = Color(0xFF4CAF50))
                            Text("Verificado por la comunidad", style = MaterialTheme.typography.bodySmall, color = Color(0xFF388E3C))
                        }
                    }
                }
                incident.flaggedFalse -> {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.small) {
                        Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.Warning, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                            Text("Marcado como falso", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                else -> {
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
                        Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.Info, null, Modifier.size(18.dp))
                            Text(
                                "Se necesitan ${3 - incident.validationScore} votos más para verificar",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            if (isOwner) {
                Text(
                    "No puedes votar en tu propio reporte",
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            } else {
                when (userVoteStatus) {
                    "none" -> {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = { onVoteTrue(incident.id) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF4CAF50).copy(alpha = 0.15f))
                            ) {
                                Icon(Icons.Filled.ThumbUp, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Confirmar")
                            }
                            FilledTonalButton(
                                onClick = { onVoteFalse(incident.id) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.12f))
                            ) {
                                Icon(Icons.Filled.ThumbDown, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Es falso")
                            }
                        }
                    }
                    "true" -> {
                        Surface(color = Color(0xFF4CAF50).copy(alpha = 0.08f), shape = MaterialTheme.shapes.small) {
                            Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Check, null, Modifier.size(18.dp), tint = Color(0xFF4CAF50))
                                Text("Confirmaste este reporte", style = MaterialTheme.typography.bodySmall, color = Color(0xFF388E3C), modifier = Modifier.weight(1f))
                                TextButton(onClick = { onRemoveVote(incident.id) }) { Text("Quitar voto", style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                    }
                    "false" -> {
                        Surface(color = MaterialTheme.colorScheme.error.copy(alpha = 0.08f), shape = MaterialTheme.shapes.small) {
                            Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Close, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                                Text("Reportaste como falso", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                                TextButton(onClick = { onRemoveVote(incident.id) }) { Text("Quitar voto", style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// HELPERS
// ==========================================
private fun formatTimeAgo(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val minutes = diff / 60000
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> "un momento"
        minutes < 60 -> "$minutes min"
        hours < 24 -> "$hours h"
        days < 7 -> "$days días"
        else -> SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(timestamp))
    }
}