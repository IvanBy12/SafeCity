package com.example.safecity.screens.reports

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.safecity.models.Incident
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    onNavigateBack: () -> Unit,
    vm: ReportsViewModel = viewModel(factory = ReportsViewModel.Factory)
) {
    val state by vm.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var generatingPdf by remember { mutableStateOf(false) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    val currentYear = remember { LocalDate.now().year }
    val years = remember(currentYear) { ((currentYear - 2)..(currentYear + 1)).toList().reversed() }
    val months = remember { (1..12).toList() }
    val weeks = remember { (1..53).toList() }
    val preview = state.preview

    LaunchedEffect(state.successMessage, state.errorMessage) {
        state.successMessage?.let { snackbarHostState.showSnackbar(it) }
        state.errorMessage?.let { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Generador de reportes") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = { vm.clearPreview() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Limpiar")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Filtros del reporte",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Text("Rango de tiempo", style = MaterialTheme.typography.labelLarge)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ReportRangeType.entries.forEach { rangeType ->
                                FilterChip(
                                    selected = state.filters.rangeType == rangeType,
                                    onClick = { vm.setRangeType(rangeType) },
                                    label = { Text(rangeType.label) }
                                )
                            }
                        }

                        when (state.filters.rangeType) {
                            ReportRangeType.MONTH -> {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    IntSelectField(
                                        label = "Año",
                                        options = years,
                                        selected = state.filters.monthYear,
                                        onSelected = vm::setMonthYear,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IntSelectField(
                                        label = "Mes",
                                        options = months,
                                        selected = state.filters.month,
                                        optionLabel = { value -> "${monthName(value)}" },
                                        onSelected = vm::setMonth,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            ReportRangeType.WEEK -> {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    IntSelectField(
                                        label = "Año",
                                        options = years,
                                        selected = state.filters.weekYear,
                                        onSelected = vm::setWeekYear,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IntSelectField(
                                        label = "Semana",
                                        options = weeks,
                                        selected = state.filters.weekOfYear,
                                        optionLabel = { value -> "Semana $value" },
                                        onSelected = vm::setWeekOfYear,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            ReportRangeType.CUSTOM -> {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    OutlinedButton(
                                        onClick = { showStartPicker = true },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Filled.DateRange, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "Inicio: ${formatShortDate(state.filters.customStartMillis)}"
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = { showEndPicker = true },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Filled.DateRange, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Fin: ${formatShortDate(state.filters.customEndMillis)}")
                                    }
                                }
                            }
                        }

                        Text("Tipo de reporte", style = MaterialTheme.typography.labelLarge)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ReportCategoryFilter.entries.forEach { option ->
                                FilterChip(
                                    selected = state.filters.categoryFilter == option,
                                    onClick = { vm.setCategoryFilter(option) },
                                    label = { Text(option.label) }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Incluir graficas")
                            Switch(
                                checked = state.filters.includeCharts,
                                onCheckedChange = vm::setIncludeCharts
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Incluir tabla de incidentes")
                            Switch(
                                checked = state.filters.includeIncidentList,
                                onCheckedChange = vm::setIncludeIncidentList
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = { vm.generatePreview() },
                                enabled = !state.isLoading,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Assessment, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Previsualizar reporte")
                            }

                            Button(
                                onClick = {
                                    val previewData = state.preview ?: return@Button
                                    generatingPdf = true
                                    scope.launch {
                                        ReportPdfGenerator.generatePdf(context, state.filters, previewData)
                                            .onSuccess { file ->
                                                vm.notifyPdfSuccess(file.name)
                                                sharePdf(context, file)
                                            }
                                            .onFailure { error ->
                                                vm.notifyPdfError(
                                                    error.message ?: "No se pudo generar el PDF"
                                                )
                                            }
                                        generatingPdf = false
                                    }
                                },
                                enabled = !state.isLoading && !generatingPdf && state.preview != null &&
                                    (state.preview?.totalIncidents ?: 0) > 0,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (generatingPdf) {
                                    CircularProgressIndicator(modifier = Modifier.width(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Filled.Download, contentDescription = null)
                                }
                                Spacer(Modifier.width(8.dp))
                                Text("Generar PDF")
                            }
                        }
                    }
                }
            }

            item {
                when {
                    state.isLoading -> {
                        LoadingStateCard()
                    }

                    preview == null -> {
                        InfoStateCard(
                            title = "Previsualizacion pendiente",
                            message = "Selecciona filtros y pulsa Previsualizar reporte."
                        )
                    }

                    preview.totalIncidents == 0 -> {
                        EmptyStateCard(
                            message = "No se encontraron incidentes con los filtros seleccionados"
                        )
                    }

                    else -> {
                        ReportPreviewCard(preview = preview, filters = state.filters)
                    }
                }
            }
        }
    }

    if (showStartPicker) {
        DatePickerSelector(
            title = "Fecha de inicio",
            initialMillis = state.filters.customStartMillis,
            onDismiss = { showStartPicker = false },
            onConfirm = {
                vm.setCustomStart(it)
                showStartPicker = false
            }
        )
    }

    if (showEndPicker) {
        DatePickerSelector(
            title = "Fecha de fin",
            initialMillis = state.filters.customEndMillis,
            onDismiss = { showEndPicker = false },
            onConfirm = {
                vm.setCustomEnd(it)
                showEndPicker = false
            }
        )
    }
}

@Composable
private fun LoadingStateCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator()
            Column {
                Text("Cargando datos para reporte...", fontWeight = FontWeight.Bold)
                Text("Estamos consultando incidentes y calculando estadisticas.")
            }
        }
    }
}

@Composable
private fun InfoStateCard(title: String, message: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column {
                Text(title, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(message)
            }
        }
    }
}

@Composable
private fun EmptyStateCard(message: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Column {
                Text("Sin resultados", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(message)
            }
        }
    }
}

@Composable
private fun ReportPreviewCard(preview: ReportPreview, filters: ReportFilters) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("Previsualizacion del reporte", style = MaterialTheme.typography.titleMedium)
            }

            Text("Rango: ${preview.rangeLabel}")
            Text("Total: ${preview.totalIncidents}")
            Text("Resumen ejecutivo: ${preview.executiveSummary}")

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricCard(
                    title = "Verificados",
                    value = preview.verifiedCount.toString(),
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "No verificados",
                    value = preview.unverifiedCount.toString(),
                    modifier = Modifier.weight(1f)
                )
            }

            ChartBlock(
                title = "Incidentes por tipo",
                data = preview.byType.entries.sortedByDescending { it.value }.map { it.toPair() }
            )

            ChartBlock(
                title = "Incidentes por estado",
                data = preview.byStatus.entries.sortedByDescending { it.value }.map { it.toPair() }
            )

            ChartBlock(
                title = "Comparativa semanal o mensual",
                data = preview.comparison.map { it.label to it.count }
            )

            if (filters.includeIncidentList) {
                IncidentListPreview(incidents = preview.incidents.take(8))
            }
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(title, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ChartBlock(title: String, data: List<Pair<String, Int>>) {
    if (data.isEmpty()) return

    val max = data.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        data.take(8).forEach { (label, value) ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(label, style = MaterialTheme.typography.bodySmall)
                    Text(value.toString(), style = MaterialTheme.typography.bodySmall)
                }
                LinearProgressIndicator(
                    progress = { value / max.toFloat() },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun IncidentListPreview(incidents: List<Incident>) {
    if (incidents.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "Listado de incidentes (preview)",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        incidents.forEach { incident ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        "${formatShortDate(incident.timestamp)} - ${incident.category.ifBlank { "Sin categoria" }}",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        statusLabel(incident.status),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        incident.address.ifBlank { "Sin zona" },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun IntSelectField(
    label: String,
    options: List<Int>,
    selected: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    optionLabel: (Int) -> String = { it.toString() }
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(optionLabel(selected), modifier = Modifier.weight(1f))
                Icon(Icons.Filled.ExpandMore, contentDescription = null)
            }
            androidx.compose.material3.DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { option ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(optionLabel(option)) },
                        onClick = {
                            onSelected(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerSelector(
    title: String,
    initialMillis: Long?,
    onDismiss: () -> Unit,
    onConfirm: (Long?) -> Unit
) {
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialMillis ?: System.currentTimeMillis()
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(pickerState.selectedDateMillis) }) {
                Text("Aceptar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            DatePicker(state = pickerState)
        }
    }
}

private fun sharePdf(context: Context, file: File) {
    val fileUri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.provider",
        file
    )

    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, fileUri)
        putExtra(Intent.EXTRA_SUBJECT, "Reporte SafeCity")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    try {
        context.startActivity(Intent.createChooser(shareIntent, "Compartir reporte PDF"))
    } catch (_: ActivityNotFoundException) {
    }
}

private fun formatShortDate(millis: Long?): String {
    millis ?: return "No seleccionada"
    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale("es", "CO"))
    return Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .format(formatter)
}