package com.example.safecity.viewmodel

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.safecity.models.Incident
import com.example.safecity.models.IncidentType
import com.example.safecity.repository.IncidentRepository
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.firestore.GeoPoint
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class DashboardUiState(
    val incidents: List<Incident> = emptyList(),
    val filteredIncidents: List<Incident> = emptyList(),
    val selectedIncident: Incident? = null,
    val userLocation: LatLng? = null,
    val filterType: IncidentType? = null,
    val showVerifiedOnly: Boolean = false,
    val loading: Boolean = true,
    val error: String? = null
)

class DashboardViewModel(
    private val repository: IncidentRepository = IncidentRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState = _uiState.asStateFlow()

    init {
        observeIncidents()
    }

    // ✅ Listener en tiempo real de Firestore
    private fun observeIncidents() {
        viewModelScope.launch {
            repository.getIncidentsFlow()
                .catch { e ->
                    _uiState.update { it.copy(error = e.message, loading = false) }
                }
                .collect { incidents ->
                    _uiState.update { state ->
                        state.copy(
                            incidents = incidents,
                            filteredIncidents = applyFilters(incidents, state),
                            loading = false
                        )
                    }
                }
        }
    }

    // ✅ Aplicar filtros
    private fun applyFilters(incidents: List<Incident>, state: DashboardUiState): List<Incident> {
        return incidents.filter { incident ->
            val matchesType = state.filterType == null || incident.type == state.filterType
            val matchesVerified = !state.showVerifiedOnly || incident.verified
            matchesType && matchesVerified
        }
    }

    // ✅ Filtrar por tipo
    fun filterByType(type: IncidentType?) {
        _uiState.update { state ->
            state.copy(
                filterType = type,
                filteredIncidents = applyFilters(state.incidents, state.copy(filterType = type))
            )
        }
    }

    // ✅ Toggle filtro de verificados
    fun toggleVerifiedFilter() {
        _uiState.update { state ->
            val newValue = !state.showVerifiedOnly
            state.copy(
                showVerifiedOnly = newValue,
                filteredIncidents = applyFilters(state.incidents, state.copy(showVerifiedOnly = newValue))
            )
        }
    }

    // ✅ Seleccionar incidente (bottom sheet)
    fun selectIncident(incident: Incident?) {
        _uiState.update { it.copy(selectedIncident = incident) }
    }

    // ✅ Actualizar ubicación del usuario
    fun updateUserLocation(location: Location) {
        _uiState.update {
            it.copy(userLocation = LatLng(location.latitude, location.longitude))
        }
    }

    // ✅ Calcular distancia entre dos puntos
    fun calculateDistance(from: LatLng, to: GeoPoint): String {
        val results = FloatArray(1)
        Location.distanceBetween(
            from.latitude, from.longitude,
            to.latitude, to.longitude,
            results
        )

        val meters = results[0]
        return when {
            meters < 1000 -> "${meters.toInt()} m"
            else -> String.format("%.1f km", meters / 1000)
        }
    }

    // ✅ Confirmar incidente
    fun confirmIncident(incidentId: String) {
        viewModelScope.launch {
            repository.confirmIncident(incidentId)
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message) }
                }
        }
    }

    // ✅ Crear nuevo incidente
    fun createIncident(incident: Incident, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            repository.createIncident(incident)
                .onSuccess {
                    _uiState.update { it.copy(loading = false) }
                    onSuccess()
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message, loading = false) }
                }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}