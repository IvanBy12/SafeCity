package com.example.safecity.screens.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.safecity.models.Incident
import com.example.safecity.models.IncidentType
import com.example.safecity.repository.IncidentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

data class ReportsUiState(
    val filters: ReportFilters = ReportFilters.defaults(),
    val isLoading: Boolean = false,
    val preview: ReportPreview? = null,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

class ReportsViewModel(
    private val repository: IncidentRepository = IncidentRepository()
) : ViewModel() {

    private val zoneId = ZoneId.systemDefault()
    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    private val _uiState = MutableStateFlow(ReportsUiState())
    val uiState: StateFlow<ReportsUiState> = _uiState.asStateFlow()

    fun setRangeType(rangeType: ReportRangeType) {
        _uiState.update {
            it.copy(
                filters = it.filters.copy(rangeType = rangeType),
                errorMessage = null,
                successMessage = null
            )
        }
    }

    fun setMonthYear(year: Int) = updateFilters { it.copy(monthYear = year) }
    fun setMonth(month: Int) = updateFilters { it.copy(month = month) }
    fun setWeekYear(year: Int) = updateFilters { it.copy(weekYear = year) }
    fun setWeekOfYear(week: Int) = updateFilters { it.copy(weekOfYear = week) }
    fun setCustomStart(millis: Long?) = updateFilters { it.copy(customStartMillis = millis) }
    fun setCustomEnd(millis: Long?) = updateFilters { it.copy(customEndMillis = millis) }

    fun setCategoryFilter(filter: ReportCategoryFilter) = updateFilters { it.copy(categoryFilter = filter) }
    fun setIncludeCharts(enabled: Boolean) = updateFilters { it.copy(includeCharts = enabled) }
    fun setIncludeIncidentList(enabled: Boolean) = updateFilters { it.copy(includeIncidentList = enabled) }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }

    fun clearPreview() {
        _uiState.update { it.copy(preview = null, errorMessage = null, successMessage = null) }
    }

    fun notifyPdfSuccess(fileName: String) {
        _uiState.update {
            it.copy(
                successMessage = "PDF generado: $fileName",
                errorMessage = null
            )
        }
    }

    fun notifyPdfError(message: String) {
        _uiState.update {
            it.copy(
                errorMessage = message,
                successMessage = null
            )
        }
    }

    fun generatePreview() {
        val filters = _uiState.value.filters
        val range = resolveRange(filters)
        if (range == null) {
            _uiState.update {
                it.copy(
                    errorMessage = "Selecciona un rango de tiempo valido",
                    successMessage = null
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }

            repository.getIncidentsOnce()
                .onSuccess { incidents ->
                    val filtered = incidents
                        .filter { it.timestamp in range.first..range.second }
                        .filter { matchesCategoryFilter(it, filters.categoryFilter) }

                    val preview = buildPreview(filtered, filters, range.first, range.second)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            preview = preview,
                            errorMessage = null
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "No se pudieron cargar los incidentes"
                        )
                    }
                }
        }
    }

    private fun updateFilters(update: (ReportFilters) -> ReportFilters) {
        _uiState.update {
            it.copy(
                filters = update(it.filters),
                errorMessage = null,
                successMessage = null
            )
        }
    }

    private fun matchesCategoryFilter(incident: Incident, filter: ReportCategoryFilter): Boolean {
        return when (filter) {
            ReportCategoryFilter.ALL -> true
            ReportCategoryFilter.VERIFIED -> incident.verified
            ReportCategoryFilter.SECURITY -> incident.type == IncidentType.SEGURIDAD
            ReportCategoryFilter.INFRASTRUCTURE -> incident.type == IncidentType.INFRAESTRUCTURA
        }
    }

    private fun buildPreview(
        incidents: List<Incident>,
        filters: ReportFilters,
        rangeStartMillis: Long,
        rangeEndMillis: Long
    ): ReportPreview {
        val byType = incidents
            .groupingBy {
                when (it.type) {
                    IncidentType.SEGURIDAD -> "Seguridad"
                    IncidentType.INFRAESTRUCTURA -> "Infraestructura"
                }
            }
            .eachCount()

        val byStatus = incidents
            .groupingBy { statusLabel(it.status) }
            .eachCount()

        val byCategory = incidents
            .groupingBy { it.category.ifBlank { "Sin categoria" } }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .associate { it.toPair() }

        val topZones = incidents
            .groupingBy { it.address.ifBlank { "Sin zona" } }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(5)
            .map { it.toPair() }

        val verifiedCount = incidents.count { it.verified }
        val unverifiedCount = incidents.size - verifiedCount

        val comparison = buildComparison(incidents, filters.rangeType)
        val rangeLabel = buildRangeLabel(filters, rangeStartMillis, rangeEndMillis)
        val dominantCategory = byCategory.entries.firstOrNull()?.key ?: "Sin datos"

        val executiveSummary = buildString {
            append("Se analizaron ${incidents.size} incidentes en $rangeLabel. ")
            append("Verificados: $verifiedCount. No verificados: $unverifiedCount. ")
            append("Categoria dominante: $dominantCategory.")
        }

        return ReportPreview(
            incidents = incidents,
            totalIncidents = incidents.size,
            verifiedCount = verifiedCount,
            unverifiedCount = unverifiedCount,
            byType = byType,
            byStatus = byStatus,
            byCategory = byCategory,
            topZones = topZones,
            comparison = comparison,
            generatedAtMillis = System.currentTimeMillis(),
            rangeStartMillis = rangeStartMillis,
            rangeEndMillis = rangeEndMillis,
            rangeLabel = rangeLabel,
            executiveSummary = executiveSummary
        )
    }

    private fun buildComparison(
        incidents: List<Incident>,
        rangeType: ReportRangeType
    ): List<ReportComparisonPoint> {
        if (incidents.isEmpty()) return emptyList()

        return when (rangeType) {
            ReportRangeType.MONTH -> {
                incidents
                    .groupingBy {
                        val date = toLocalDate(it.timestamp)
                        date.get(WeekFields.ISO.weekOfMonth())
                    }
                    .eachCount()
                    .toSortedMap()
                    .map { (week, count) -> ReportComparisonPoint("Semana $week", count) }
            }

            ReportRangeType.WEEK,
            ReportRangeType.CUSTOM -> {
                incidents
                    .groupingBy { toLocalDate(it.timestamp) }
                    .eachCount()
                    .toSortedMap()
                    .map { (date, count) ->
                        ReportComparisonPoint(date.format(DateTimeFormatter.ofPattern("dd/MM")), count)
                    }
            }
        }
    }

    private fun buildRangeLabel(filters: ReportFilters, startMillis: Long, endMillis: Long): String {
        return when (filters.rangeType) {
            ReportRangeType.MONTH -> "${monthName(filters.month)} ${filters.monthYear}"
            ReportRangeType.WEEK -> {
                "Semana ${filters.weekOfYear} de ${filters.weekYear} (${formatDate(startMillis)} - ${formatDate(endMillis)})"
            }
            ReportRangeType.CUSTOM -> "${formatDate(startMillis)} - ${formatDate(endMillis)}"
        }
    }

    private fun resolveRange(filters: ReportFilters): Pair<Long, Long>? {
        return when (filters.rangeType) {
            ReportRangeType.MONTH -> {
                if (filters.month !in 1..12) return null
                val startDate = LocalDate.of(filters.monthYear, filters.month, 1)
                val endDate = startDate.plusMonths(1).minusDays(1)
                startDate.startOfDayMillis() to endDate.endOfDayMillis()
            }

            ReportRangeType.WEEK -> {
                if (filters.weekOfYear !in 1..53) return null
                val jan4 = LocalDate.of(filters.weekYear, 1, 4)
                val firstWeekStart = jan4.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val startDate = firstWeekStart.plusWeeks((filters.weekOfYear - 1).toLong())
                val endDate = startDate.plusDays(6)
                startDate.startOfDayMillis() to endDate.endOfDayMillis()
            }

            ReportRangeType.CUSTOM -> {
                val start = filters.customStartMillis ?: return null
                val end = filters.customEndMillis ?: return null
                val startDate = toLocalDate(start)
                val endDate = toLocalDate(end)
                if (startDate.isAfter(endDate)) return null
                startDate.startOfDayMillis() to endDate.endOfDayMillis()
            }
        }
    }

    private fun LocalDate.startOfDayMillis(): Long =
        atStartOfDay(zoneId).toInstant().toEpochMilli()

    private fun LocalDate.endOfDayMillis(): Long =
        plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1

    private fun toLocalDate(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zoneId).toLocalDate()

    private fun formatDate(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(zoneId).toLocalDate().format(dateFormatter)

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ReportsViewModel() as T
            }
        }
    }
}