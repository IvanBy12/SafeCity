package com.example.safecity.screens.statistics

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import com.example.safecity.network.*
import com.example.safecity.repository.IncidentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.util.*

// ==========================================
// VIEW MODEL
// ==========================================

class StatisticsViewModel(
    private val repo: IncidentRepository = IncidentRepository()
) : ViewModel() {

    private val _stats   = MutableStateFlow<AggregateStatsResponse?>(null)
    val stats: StateFlow<AggregateStatsResponse?> = _stats.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error   = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Período seleccionado: null = últimos 12 meses
    private val _selectedYear  = MutableStateFlow<Int?>(null)
    private val _selectedMonth = MutableStateFlow<Int?>(null)
    val selectedYear:  StateFlow<Int?> = _selectedYear.asStateFlow()
    val selectedMonth: StateFlow<Int?> = _selectedMonth.asStateFlow()

    private var cachedIncidents: List<Incident> = emptyList()
    private var lastValidPeriod: Pair<Int?, Int?> = null to null

    init {
        observeIncidents()
        loadStats()
        viewModelScope.launch { repo.refreshIncidentsNow() }
    }

    fun loadStats(year: Int? = null, month: Int? = null, silent: Boolean = false) {
        val (safeYear, safeMonth) = normalizePeriod(year = year, month = month)
        _selectedYear.value = safeYear
        _selectedMonth.value = safeMonth

        recomputeStats(silent = silent)

        if (!silent) {
            viewModelScope.launch {
                repo.refreshIncidentsNow()
                    .onFailure { if (cachedIncidents.isEmpty()) _error.value = it.message }
            }
        }
    }

    private fun observeIncidents() {
        viewModelScope.launch {
            repo.getIncidentsFlow().collectLatest { incidents ->
                cachedIncidents = incidents
                recomputeStats(silent = true)
            }
        }
    }

    private fun recomputeStats(silent: Boolean) {
        if (!silent) _loading.value = true
        _error.value = null

        _stats.value = buildStatsFromIncidents(
            incidents = cachedIncidents,
            year = _selectedYear.value,
            month = _selectedMonth.value
        )

        _loading.value = false
    }

    private fun normalizePeriod(year: Int?, month: Int?): Pair<Int?, Int?> {
        if (year == null && month == null) {
            lastValidPeriod = null to null
            return lastValidPeriod
        }

        if (year != null && month != null && month in 1..12) {
            lastValidPeriod = year to month
            return lastValidPeriod
        }

        return lastValidPeriod
    }

    private fun buildStatsFromIncidents(
        incidents: List<Incident>,
        year: Int?,
        month: Int?
    ): AggregateStatsResponse {
        val zoneId = ZoneId.systemDefault()
        val periodType = if (year != null && month != null) "month" else "last12months"

        val filtered = filterByPeriod(incidents, year, month, zoneId)

        val summary = StatsSummary(
            total = filtered.size,
            pending = filtered.count { it.status == "pending" },
            verified = filtered.count { it.status == "verified" || it.verified },
            closed = filtered.count { it.status == "closed" },
            avgScore = filtered.map { it.validationScore.toDouble() }
                .average()
                .let { if (it.isNaN()) 0.0 else (kotlin.math.round(it * 100.0) / 100.0) }
        )

        val byTypeAndStatus = filtered
            .groupBy { Triple(it.type.name, it.category.ifBlank { "Sin tipo" }, it.status.ifBlank { "pending" }) }
            .map { (key, values) ->
                TypeStatusStat(
                    _id = TypeStatusId(
                        categoryGroup = key.first,
                        type = key.second,
                        status = key.third
                    ),
                    count = values.size,
                    avgScore = values.map { it.validationScore.toDouble() }
                        .average()
                        .let { if (it.isNaN()) 0.0 else (kotlin.math.round(it * 100.0) / 100.0) }
                )
            }
            .sortedByDescending { it.count }

        val byTimeBand = filtered
            .groupBy { Pair(timeBand(it.timestamp, zoneId), it.type.name) }
            .map { (key, values) ->
                TimeBandStat(
                    _id = TimeBandId(
                        timeBand = key.first,
                        categoryGroup = key.second
                    ),
                    count = values.size
                )
            }
            .sortedByDescending { it.count }

        val byLocality = filtered
            .groupBy { Pair(localityFromAddress(it.address), it.type.name) }
            .map { (key, values) ->
                LocalityStat(
                    _id = LocalityId(
                        locality = key.first,
                        categoryGroup = key.second
                    ),
                    count = values.size,
                    verified = values.count { it.status == "verified" || it.verified }
                )
            }
            .sortedByDescending { it.count }

        val byMonth = filtered
            .groupBy {
                val ym = YearMonth.from(Instant.ofEpochMilli(it.timestamp).atZone(zoneId))
                Triple(ym.year, ym.monthValue, it.type.name)
            }
            .map { (key, values) ->
                MonthStat(
                    _id = MonthStatId(
                        year = key.first,
                        month = key.second,
                        categoryGroup = key.third
                    ),
                    count = values.size,
                    verified = values.count { it.status == "verified" || it.verified },
                    closed = values.count { it.status == "closed" }
                )
            }
            .sortedWith(compareByDescending<MonthStat> { it._id.year }.thenByDescending { it._id.month })

        return AggregateStatsResponse(
            success = true,
            generatedAt = Instant.now().toString(),
            period = StatsPeriod(type = periodType, year = year, month = month),
            data = AggregateStatsData(
                summary = summary,
                byTypeAndStatus = byTypeAndStatus,
                byTimeBand = byTimeBand,
                byLocality = byLocality,
                byMonth = byMonth
            )
        )
    }

    private fun filterByPeriod(
        incidents: List<Incident>,
        year: Int?,
        month: Int?,
        zoneId: ZoneId
    ): List<Incident> {
        if (year != null && month != null) {
            return incidents.filter {
                val ym = YearMonth.from(Instant.ofEpochMilli(it.timestamp).atZone(zoneId))
                ym.year == year && ym.monthValue == month
            }
        }

        val end = YearMonth.now(zoneId)
        val start = end.minusMonths(11)
        return incidents.filter {
            val ym = YearMonth.from(Instant.ofEpochMilli(it.timestamp).atZone(zoneId))
            ym >= start && ym <= end
        }
    }

    private fun timeBand(timestamp: Long, zoneId: ZoneId): String {
        val hour = Instant.ofEpochMilli(timestamp).atZone(zoneId).hour
        return when (hour) {
            in 0..5 -> "00-06"
            in 6..11 -> "06-12"
            in 12..17 -> "12-18"
            else -> "18-24"
        }
    }

    private fun localityFromAddress(address: String): String {
        val trimmed = address.trim()
        if (trimmed.isBlank()) return "Sin localidad"

        val firstToken = trimmed.substringBefore(',').trim()
        if (firstToken.isNotBlank()) return firstToken

        return trimmed
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                StatisticsViewModel() as T
        }
    }
}

// ==========================================
// SCREEN
// ==========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    onNavigateBack: () -> Unit,
    vm: StatisticsViewModel = viewModel(factory = StatisticsViewModel.Factory)
) {
    val stats   by vm.stats.collectAsState()
    val loading by vm.loading.collectAsState()
    val error   by vm.error.collectAsState()
    val selYear  by vm.selectedYear.collectAsState()
    val selMonth by vm.selectedMonth.collectAsState()

    val currentYear  = Calendar.getInstance().get(Calendar.YEAR)
    val currentMonth = Calendar.getInstance().get(Calendar.MONTH) + 1

    // Lista de meses para el selector
    val monthNames = listOf(
        "Ene", "Feb", "Mar", "Abr", "May", "Jun",
        "Jul", "Ago", "Sep", "Oct", "Nov", "Dic"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Estadísticas Agregadas") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = { vm.loadStats(selYear, selMonth) }) {
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
            // ─── SELECTOR DE PERÍODO ──────────────────────────────────
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 2.dp
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "Período",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier              = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Últimos 12 meses
                        FilterChip(
                            selected = selYear == null,
                            onClick  = { vm.loadStats() },
                            label    = { Text("Últimos 12 meses") },
                            leadingIcon = if (selYear == null) ({
                                Icon(Icons.Filled.Check, null, Modifier.size(16.dp))
                            }) else null
                        )
                        // Mes a mes del año actual
                        (1..currentMonth).reversed().forEach { month ->
                            FilterChip(
                                selected = selYear == currentYear && selMonth == month,
                                onClick  = { vm.loadStats(currentYear, month) },
                                label    = { Text("${monthNames[month - 1]} $currentYear") }
                            )
                        }
                        // Meses del año anterior
                        (1..12).reversed().forEach { month ->
                            FilterChip(
                                selected = selYear == (currentYear - 1) && selMonth == month,
                                onClick  = { vm.loadStats(currentYear - 1, month) },
                                label    = { Text("${monthNames[month - 1]} ${currentYear - 1}") }
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(8.dp))
                        Text("Calculando estadísticas...", style = MaterialTheme.typography.bodySmall)
                    }
                }
                error != null -> Box(
                    Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.ErrorOutline, null,
                            Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(error ?: "Error", color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { vm.loadStats(selYear, selMonth) }) {
                            Text("Reintentar")
                        }
                    }
                }
                stats != null -> {
                    val s = stats!!
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Nota de privacidad
                        item {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Row(
                                    modifier           = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment  = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Filled.PrivacyTip, null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Column {
                                        Text(
                                            "Datos 100% anónimos",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            "Sin nombres, UIDs ni datos personales. Generado: ${
                                                s.generatedAt.take(10)
                                            }",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                        }

                        // ─── RESUMEN GENERAL ──────────────────────────────
                        item {
                            StatsSection(title = "Resumen General") {
                                Row(
                                    modifier              = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    SummaryCard("Total", "${s.data.summary.total}", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                                    SummaryCard("Pendientes", "${s.data.summary.pending}", Color(0xFFE65100), Modifier.weight(1f))
                                    SummaryCard("Verificados", "${s.data.summary.verified}", Color(0xFF2E7D32), Modifier.weight(1f))
                                    SummaryCard("Cerrados", "${s.data.summary.closed}", Color(0xFF616161), Modifier.weight(1f))
                                }
                            }
                        }

                        // ─── POR TIPO DE INCIDENTE ────────────────────────
                        item {
                            StatsSection(title = "Por Tipo de Incidente") {
                                // Agrupa por tipo sumando todos los estados
                                val byType = s.data.byTypeAndStatus
                                    .groupBy { it._id.type }
                                    .mapValues { (_, items) -> items.sumOf { it.count } }
                                    .entries
                                    .sortedByDescending { it.value }

                                val total = byType.sumOf { it.value }.coerceAtLeast(1)

                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    byType.forEach { (type, count) ->
                                        TypeBar(
                                            label    = type,
                                            count    = count,
                                            fraction = count.toFloat() / total
                                        )
                                    }
                                }
                            }
                        }

                        // ─── POR FRANJA HORARIA ───────────────────────────
                        item {
                            StatsSection(title = "Por Franja Horaria") {
                                val byBand = s.data.byTimeBand
                                    .groupBy { it._id.timeBand }
                                    .mapValues { (_, items) -> items.sumOf { it.count } }
                                    .entries
                                    .sortedByDescending { it.value }

                                val total = byBand.sumOf { it.value }.coerceAtLeast(1)

                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    byBand.forEach { (band, count) ->
                                        TypeBar(
                                            label    = band,
                                            count    = count,
                                            fraction = count.toFloat() / total,
                                            color    = Color(0xFF1565C0)
                                        )
                                    }
                                }
                            }
                        }

                        // ─── POR LOCALIDAD ────────────────────────────────
                        if (s.data.byLocality.isNotEmpty()) {
                            item {
                                StatsSection(title = "Por Localidad (Top 10)") {
                                    val byLocality = s.data.byLocality
                                        .groupBy { it._id.locality }
                                        .mapValues { (_, items) ->
                                            val count    = items.sumOf { it.count }
                                            val verified = items.sumOf { it.verified }
                                            count to verified
                                        }
                                        .entries
                                        .sortedByDescending { it.value.first }
                                        .take(10)

                                    val max = byLocality.firstOrNull()?.value?.first ?: 1

                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        byLocality.forEach { (locality, data) ->
                                            val (count, verified) = data
                                            LocalityRow(
                                                name     = locality,
                                                count    = count,
                                                verified = verified,
                                                fraction = count.toFloat() / max
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // ─── EVOLUCIÓN MENSUAL ────────────────────────────
                        if (s.data.byMonth.isNotEmpty()) {
                            item {
                                StatsSection(title = "Evolución Mensual") {
                                    val monthly = s.data.byMonth
                                        .groupBy { "${it._id.year}-${it._id.month.toString().padStart(2, '0')}" }
                                        .mapValues { (_, items) ->
                                            Triple(
                                                items.sumOf { it.count },
                                                items.sumOf { it.verified },
                                                items.sumOf { it.closed }
                                            )
                                        }
                                        .entries
                                        .sortedByDescending { it.key }
                                        .take(12)

                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        // Header
                                        Row(modifier = Modifier.fillMaxWidth()) {
                                            Text("Mes", Modifier.weight(2f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                            Text("Total", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                            Text("Verif.", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                            Text("Cerr.", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        }
                                        HorizontalDivider()
                                        monthly.forEach { (yearMonth, data) ->
                                            val (total, verified, closed) = data
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(yearMonth, Modifier.weight(2f), style = MaterialTheme.typography.bodySmall)
                                                Text("$total",    Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                                Text("$verified", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = Color(0xFF2E7D32))
                                                Text("$closed",   Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = Color(0xFF616161))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }
}

// ==========================================
// COMPONENTES DE UI
// ==========================================

@Composable
private fun StatsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                title,
                style      = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SummaryCard(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors   = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Column(
            modifier              = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalAlignment   = Alignment.CenterHorizontally
        ) {
            Text(
                value,
                style      = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color      = color
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TypeBar(
    label: String,
    count: Int,
    fraction: Float,
    color: Color = MaterialTheme.colorScheme.primary
) {
    Column {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text(
                "$count",
                style      = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(2.dp))
        LinearProgressIndicator(
            progress        = { fraction },
            modifier        = Modifier.fillMaxWidth().height(6.dp),
            color           = color,
            trackColor      = color.copy(alpha = 0.15f)
        )
    }
}

@Composable
private fun LocalityRow(
    name: String,
    count: Int,
    verified: Int,
    fraction: Float
) {
    Column {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "$count total",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "$verified verif.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF2E7D32)
                )
            }
        }
        LinearProgressIndicator(
            progress   = { fraction },
            modifier   = Modifier.fillMaxWidth().height(4.dp),
            color      = MaterialTheme.colorScheme.tertiary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

