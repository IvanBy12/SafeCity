package com.example.safecity.screens.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
    userVoteStatus: String,  // "none" | "true" | "false"
    calculateDistance: (LatLng, GeoPoint) -> String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ========================================
        // HEADER
        // ========================================
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

            // Badge de estado
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

        Divider()

        // ========================================
        // DESCRIPCIÓN
        // ========================================
        if (incident.description.isNotBlank()) {
            Text(incident.description, style = MaterialTheme.typography.bodyMedium)
        }

        // ========================================
        // UBICACIÓN Y DISTANCIA
        // ========================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
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
                    Text(
                        calculateDistance(userLocation, incident.location),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // ========================================
        // FECHA
        // ========================================
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Filled.AccessTime, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatTimestamp(incident.timestamp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Divider()

        // ========================================
        // BARRA DE VALIDACIÓN COMUNITARIA
        // ========================================
        ValidationScoreSection(
            incident = incident,
            isOwner = isOwner,
            userVoteStatus = userVoteStatus,
            onVoteTrue = onVoteTrue,
            onVoteFalse = onVoteFalse,
            onRemoveVote = onRemoveVote
        )
    }
}

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
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Título
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.HowToVote, null, tint = MaterialTheme.colorScheme.primary)
                Text("Validación comunitaria", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            // Score visual
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Votos verdaderos
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.ThumbUp, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(24.dp))
                    Text(
                        "${incident.votedTrueCount}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4CAF50)
                    )
                    Text("Confirman", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                // Score neto
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

                // Votos falsos
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.ThumbDown, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(24.dp))
                    Text(
                        "${incident.votedFalseCount}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text("Niegan", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Barra de progreso visual
            val totalVotes = incident.votedTrueCount + incident.votedFalseCount
            if (totalVotes > 0) {
                val trueRatio = incident.votedTrueCount.toFloat() / totalVotes.toFloat()
                LinearProgressIndicator(
                    progress = { trueRatio },
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF4CAF50),
                    trackColor = MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                )
            }

            // Mensaje de estado
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
                            Text("Marcado como falso por la comunidad", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                incident.validationScore < 3 && incident.validationScore > -5 -> {
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
                        Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.Info, null, Modifier.size(18.dp))
                            Text(
                                "Se necesitan ${3 - incident.validationScore} votos positivos más para verificar",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            Divider()

            // ========================================
            // BOTONES DE VOTACIÓN
            // ========================================
            if (isOwner) {
                // El creador no puede votar
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        "No puedes votar en tu propio reporte",
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                when (userVoteStatus) {
                    "none" -> {
                        // No ha votado → mostrar ambos botones
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Confirmar (verdadero)
                            FilledTonalButton(
                                onClick = { onVoteTrue(incident.id) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = Color(0xFF4CAF50).copy(alpha = 0.15f)
                                )
                            ) {
                                Icon(Icons.Filled.ThumbUp, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Confirmar")
                            }

                            // Reportar como falso
                            FilledTonalButton(
                                onClick = { onVoteFalse(incident.id) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                                )
                            ) {
                                Icon(Icons.Filled.ThumbDown, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Es falso")
                            }
                        }
                    }

                    "true" -> {
                        // Ya votó verdadero
                        Surface(
                            color = Color(0xFF4CAF50).copy(alpha = 0.08f),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Check, null, Modifier.size(18.dp), tint = Color(0xFF4CAF50))
                                Text("Confirmaste este reporte", style = MaterialTheme.typography.bodySmall, color = Color(0xFF388E3C), modifier = Modifier.weight(1f))
                                TextButton(onClick = { onRemoveVote(incident.id) }) {
                                    Text("Quitar voto", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }

                    "false" -> {
                        // Ya votó falso
                        Surface(
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Close, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                                Text("Reportaste como falso", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                                TextButton(onClick = { onRemoveVote(incident.id) }) {
                                    Text("Quitar voto", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}