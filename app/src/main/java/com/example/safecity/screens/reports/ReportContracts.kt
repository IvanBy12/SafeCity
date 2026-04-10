package com.example.safecity.screens.reports

import com.example.safecity.models.Incident
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale

enum class ReportRangeType(val label: String) {
    MONTH("Mes"),
    WEEK("Semana"),
    CUSTOM("Rango")
}

enum class ReportCategoryFilter(val label: String) {
    ALL("Todos"),
    VERIFIED("Verificados"),
    SECURITY("Seguridad"),
    INFRASTRUCTURE("Infraestructura")
}

data class ReportFilters(
    val rangeType: ReportRangeType,
    val monthYear: Int,
    val month: Int,
    val weekYear: Int,
    val weekOfYear: Int,
    val customStartMillis: Long?,
    val customEndMillis: Long?,
    val categoryFilter: ReportCategoryFilter,
    val includeCharts: Boolean,
    val includeIncidentList: Boolean
) {
    companion object {
        fun defaults(now: LocalDate = LocalDate.now()): ReportFilters {
            val iso = WeekFields.ISO
            return ReportFilters(
                rangeType = ReportRangeType.MONTH,
                monthYear = now.year,
                month = now.monthValue,
                weekYear = now.year,
                weekOfYear = now.get(iso.weekOfWeekBasedYear()),
                customStartMillis = null,
                customEndMillis = null,
                categoryFilter = ReportCategoryFilter.ALL,
                includeCharts = true,
                includeIncidentList = true
            )
        }
    }
}

data class ReportComparisonPoint(
    val label: String,
    val count: Int
)

data class ReportPreview(
    val incidents: List<Incident>,
    val totalIncidents: Int,
    val verifiedCount: Int,
    val unverifiedCount: Int,
    val byType: Map<String, Int>,
    val byStatus: Map<String, Int>,
    val byCategory: Map<String, Int>,
    val topZones: List<Pair<String, Int>>,
    val comparison: List<ReportComparisonPoint>,
    val generatedAtMillis: Long,
    val rangeStartMillis: Long,
    val rangeEndMillis: Long,
    val rangeLabel: String,
    val executiveSummary: String
)

fun monthName(month: Int): String {
    val names = listOf(
        "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
        "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
    )
    return names.getOrElse(month - 1) { month.toString() }
}

fun statusLabel(status: String): String = when (status.lowercase(Locale.ROOT)) {
    "verified" -> "Verificados"
    "closed" -> "Cerrados"
    else -> "Pendientes"
}