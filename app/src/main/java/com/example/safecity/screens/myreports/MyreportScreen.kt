package com.example.safecity.screens.myreports

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.safecity.models.Incident
import com.example.safecity.repository.IncidentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// ==========================================
// VIEW MODEL
// ==========================================

class MyReportsViewModel(
    private val repo: IncidentRepository = IncidentRepository()
) : ViewModel() {

    private val _incidents  = MutableStateFlow<List<Incident>>(emptyList())
    val incidents: StateFlow<List<Incident>> = _incidents.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error   = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Filtro de estado activo: null = todos
    private val _filter  = MutableStateFlow<String?>(null)
    val filter: StateFlow<String?> = _filter.asStateFlow()

    init {
        loadMyIncidents()
        observeRepositoryUpdates()
    }

    fun loadMyIncidents(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) _loading.value = true
            _error.value = null
            repo.getMyIncidents(status = _filter.value)
                .onSuccess  { _incidents.value = it }
                .onFailure  { _error.value = it.message }
            if (!silent) _loading.value = false
        }
    }


    private fun observeRepositoryUpdates() {
        viewModelScope.launch {
            repo.observeDataChanges().collectLatest {
                loadMyIncidents(silent = true)
            }
        }
    }
    fun setFilter(status: String?) {
        _filter.value = status
        loadMyIncidents()
    }

    fun deleteIncident(id: String, onDone: () -> Unit) {
        viewModelScope.launch {
            repo.deleteIncident(id)
                .onSuccess {
                    _incidents.update { current -> current.filterNot { it.id == id } }
                    onDone()
                    loadMyIncidents(silent = true)
                }
                .onFailure { _error.value = it.message }
        }
    }

    fun editIncident(id: String, newDescription: String, onDone: () -> Unit) {
        viewModelScope.launch {
            repo.editIncident(id, newDescription)
                .onSuccess {
                    _incidents.update { current ->
                        current.map { incident ->
                            if (incident.id == id) incident.copy(description = newDescription)
                            else incident
                        }
                    }
                    onDone()
                    loadMyIncidents(silent = true)
                }
                .onFailure { _error.value = it.message }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                MyReportsViewModel() as T
        }
    }
}

// ==========================================
// SCREEN
// ==========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyReportsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    vm: MyReportsViewModel = viewModel(factory = MyReportsViewModel.Factory)
) {
    val incidents by vm.incidents.collectAsState()
    val loading   by vm.loading.collectAsState()
    val error     by vm.error.collectAsState()
    val filter    by vm.filter.collectAsState()

    var incidentToDelete by remember { mutableStateOf<Incident?>(null) }
    var incidentToEdit   by remember { mutableStateOf<Incident?>(null) }

    // Conteos por estado para los chips
    val allIncidents = incidents
    val counts = mapOf(
        null       to allIncidents.size,
        "pending"  to allIncidents.count { it.status == "pending" },
        "verified" to allIncidents.count { it.status == "verified" },
        "closed"   to allIncidents.count { it.status == "closed" }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mis Reportes") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = { vm.loadMyIncidents() }) {
                        Icon(Icons.Filled.Refresh, "Actualizar")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ─── FILTROS DE ESTADO ───────────────────────────────────────
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusFilterChip(
                    label    = "Todos (${counts[null]})",
                    selected = filter == null,
                    onClick  = { vm.setFilter(null) }
                )
                StatusFilterChip(
                    label    = "⏳ Pendientes (${counts["pending"]})",
                    selected = filter == "pending",
                    onClick  = { vm.setFilter("pending") }
                )
                StatusFilterChip(
                    label    = "✅ Verificados (${counts["verified"]})",
                    selected = filter == "verified",
                    onClick  = { vm.setFilter("verified") }
                )
                StatusFilterChip(
                    label    = "🔒 Cerrados (${counts["closed"]})",
                    selected = filter == "closed",
                    onClick  = { vm.setFilter("closed") }
                )
            }

            HorizontalDivider()

            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                error != null -> Box(
                    Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment   = Alignment.CenterHorizontally,
                        verticalArrangement   = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Filled.ErrorOutline, null,
                            Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            error ?: "Error desconocido",
                            color = MaterialTheme.colorScheme.error
                        )
                        Button(onClick = { vm.loadMyIncidents() }) {
                            Text("Reintentar")
                        }
                    }
                }
                incidents.isEmpty() -> Box(
                    Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment   = Alignment.CenterHorizontally,
                        verticalArrangement   = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Filled.ListAlt, null,
                            Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outlineVariant
                        )
                        Text(
                            if (filter == null) "No tienes reportes aún"
                            else "No tienes reportes con este estado",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(incidents, key = { it.id }) { incident ->
                        MyReportCard(
                            incident          = incident,
                            onView            = { onNavigateToDetail(incident.id) },
                            onEdit            = { incidentToEdit = incident },
                            onDelete          = { incidentToDelete = incident }
                        )
                    }
                }
            }
        }
    }

    // ─── DIALOGO CONFIRMACIÓN ELIMINAR ──────────────────────────────
    incidentToDelete?.let { inc ->
        AlertDialog(
            onDismissRequest = { incidentToDelete = null },
            icon    = { Icon(Icons.Filled.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
            title   = { Text("Eliminar reporte") },
            text    = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("¿Estás seguro que deseas eliminar este reporte?")
                    Text(
                        "\"${inc.category} — ${inc.address}\"",
                        style    = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (!inc.isEditable) {
                        Text(
                            "⚠️ El plazo de edición venció. Solo puedes eliminarlo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteIncident(inc.id) {}
                        incidentToDelete = null
                    }
                ) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { incidentToDelete = null }) { Text("Cancelar") }
            }
        )
    }

    // ─── DIALOGO EDICIÓN ────────────────────────────────────────────
    incidentToEdit?.let { inc ->
        EditIncidentDialog(
            incident = inc,
            onDismiss = { incidentToEdit = null },
            onSave = { newDesc ->
                vm.editIncident(inc.id, newDesc) {}
                incidentToEdit = null
            }
        )
    }
}

// ==========================================
// CARD DE REPORTE
// ==========================================

@Composable
private fun MyReportCard(
    incident: Incident,
    onView: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val statusInfo = statusDisplayInfo(incident.status)

    Card(
        onClick  = onView,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Cabecera: categoría + badge de estado
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        incident.category,
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        incident.address.ifBlank { "Sin dirección" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusBadge(label = statusInfo.label, color = statusInfo.color)
            }

            Spacer(Modifier.height(8.dp))

            // Descripción
            if (incident.description.isNotBlank()) {
                Text(
                    incident.description,
                    style   = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    color   = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
            }

            // Metadata
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                // Fecha
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.CalendarToday, null,
                        Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        formatDate(incident.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Votos
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.ThumbUp, null,
                        Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "${incident.votedTrueCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Anónimo
                if (incident.isAnonymous) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.VisibilityOff, null,
                            Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            "Anónimo",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            // ─── REQ 2: indicadores de tiempo para reportes activos ───
            if (incident.status != "closed") {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    // Tiempo restante de edición
                    if (incident.isEditable && incident.editSecondsLeft > 0) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Timer, null,
                                Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "Editable por ${formatSeconds(incident.editSecondsLeft)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        Text(
                            "Solo eliminable",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Días hasta cierre
                    if (incident.daysUntilClose > 0) {
                        Text(
                            "Cierra en ${incident.daysUntilClose} días",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (incident.daysUntilClose <= 3)
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ─── ACCIONES ───
            Spacer(Modifier.height(8.dp))
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                // Editar solo si está dentro del plazo y no está cerrado
                if (incident.isEditable) {
                    TextButton(onClick = onEdit) {
                        Icon(Icons.Filled.Edit, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Editar")
                    }
                }
                // Eliminar siempre disponible (si no está cerrado el reportero puede eliminar)
                if (incident.status != "closed") {
                    TextButton(
                        onClick = onDelete,
                        colors  = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Filled.Delete, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Eliminar")
                    }
                }
            }
        }
    }
}

// ==========================================
// DIALOGO DE EDICIÓN
// ==========================================

@Composable
private fun EditIncidentDialog(
    incident: Incident,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var description by remember { mutableStateOf(incident.description) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon    = { Icon(Icons.Filled.Edit, null) },
        title   = { Text("Editar reporte") },
        text    = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "⚠️ Solo puedes editar dentro de los 15 minutos de haber creado el reporte.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (incident.editSecondsLeft > 0) {
                    Text(
                        "Tiempo restante: ${formatSeconds(incident.editSecondsLeft)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value         = description,
                    onValueChange = { description = it },
                    label         = { Text("Descripción") },
                    modifier      = Modifier.fillMaxWidth(),
                    minLines      = 3
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick  = { onSave(description) },
                enabled  = description.isNotBlank()
            ) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

// ==========================================
// HELPERS DE UI
// ==========================================

@Composable
private fun StatusFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick  = onClick,
        label    = { Text(label, style = MaterialTheme.typography.labelSmall) }
    )
}

@Composable
private fun StatusBadge(label: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            label,
            modifier   = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style      = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color      = color
        )
    }
}

private data class StatusInfo(val label: String, val color: Color)

@Composable
private fun statusDisplayInfo(status: String): StatusInfo = when (status) {
    "verified" -> StatusInfo("✅ Verificado", Color(0xFF2E7D32))  // verde
    "closed"   -> StatusInfo("🔒 Cerrado",   Color(0xFF616161))  // gris
    else       -> StatusInfo("⏳ Pendiente", Color(0xFFE65100))  // naranja
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("es", "CO"))
    return sdf.format(Date(timestamp))
}

private fun formatSeconds(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return if (m > 0) "${m}m ${s}s" else "${s}s"
}

