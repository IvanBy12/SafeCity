package com.example.safecity.screens.statistics

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.safecity.models.IncidentType
import com.example.safecity.repository.IncidentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

data class StatisticsUiState(
    val totalIncidents: Int = 0,
    val verifiedIncidents: Int = 0,
    val securityIncidents: Int = 0,
    val infrastructureIncidents: Int = 0,
    val incidentsToday: Int = 0,
    val topIncidentTypes: List<Pair<String, Int>> = emptyList(),
    val averageConfirmations: Double = 0.0,
    val loading: Boolean = false,
    val error: String? = null
)

class StatisticsViewModel(
    private val repository: IncidentRepository = IncidentRepository()
) : ViewModel() {

    private val TAG = "StatisticsViewModel"

    private val _uiState = MutableStateFlow(StatisticsUiState())
    val uiState = _uiState.asStateFlow()

    fun loadStatistics() {
        Log.d(TAG, "📊 Cargando estadísticas...")

        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }

            repository.getIncidentsFlow()
                .collect { incidents ->
                    Log.d(TAG, "📊 Procesando ${incidents.size} incidentes")

                    // Calcular estadísticas
                    val total = incidents.size
                    val verified = incidents.count { it.verified }
                    val security = incidents.count { it.type == IncidentType.SEGURIDAD }
                    val infrastructure = incidents.count { it.type == IncidentType.INFRAESTRUCTURA }

                    // Incidentes de hoy
                    val today = System.currentTimeMillis()
                    val oneDayAgo = today - TimeUnit.DAYS.toMillis(1)
                    val incidentsToday = incidents.count { it.timestamp >= oneDayAgo }

                    // Top 5 tipos más reportados
                    val typeCount = incidents
                        .groupBy { it.category }
                        .mapValues { it.value.size }
                        .toList()
                        .sortedByDescending { it.second }
                        .take(5)

                    // Promedio de confirmaciones
                    val avgConfirmations = if (incidents.isNotEmpty()) {
                        incidents.sumOf { it.confirmations }.toDouble() / incidents.size
                    } else {
                        0.0
                    }

                    _uiState.update {
                        it.copy(
                            totalIncidents = total,
                            verifiedIncidents = verified,
                            securityIncidents = security,
                            infrastructureIncidents = infrastructure,
                            incidentsToday = incidentsToday,
                            topIncidentTypes = typeCount,
                            averageConfirmations = String.format("%.1f", avgConfirmations).toDouble(),
                            loading = false
                        )
                    }

                    Log.d(TAG, "✅ Estadísticas calculadas:")
                    Log.d(TAG, "  - Total: $total")
                    Log.d(TAG, "  - Verificados: $verified")
                    Log.d(TAG, "  - Seguridad: $security")
                    Log.d(TAG, "  - Infraestructura: $infrastructure")
                    Log.d(TAG, "  - Hoy: $incidentsToday")
                    Log.d(TAG, "  - Promedio confirmaciones: $avgConfirmations")

                    // Solo recolectar una vez
                    return@collect
                }
        }
    }
}
>>>>>>> Stashed changes
