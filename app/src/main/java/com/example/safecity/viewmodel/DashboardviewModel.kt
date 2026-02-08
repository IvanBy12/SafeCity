package com.example.safecity.viewmodel

import android.location.Location
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.safecity.models.Incident
import com.example.safecity.models.IncidentType
import com.example.safecity.repository.IncidentRepository
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseAuth
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
    val loading: Boolean = false,  // ✅ Cambiado a false para no mostrar loading inicial
    val error: String? = null,
    val currentUserId: String? = null  // ✅ NUEVO: Para saber si el usuario ya confirmó
)

class DashboardViewModel(
    private val repository: IncidentRepository = IncidentRepository()
) : ViewModel() {

    private val TAG = "DashboardViewModel"

    private val _uiState = MutableStateFlow(DashboardUiState(
        currentUserId = FirebaseAuth.getInstance().currentUser?.uid  // ✅ Obtener userId
    ))
    val uiState = _uiState.asStateFlow()

    init {
        Log.d(TAG, "🚀 DashboardViewModel inicializado")
        observeIncidents()
    }

    // ==========================================
    // OBSERVAR INCIDENTES (con polling automático)
    // ==========================================

    private fun observeIncidents() {
        Log.d(TAG, "👀 Iniciando observación de incidentes con polling...")

        viewModelScope.launch {
            repository.getIncidentsFlow()
                .catch { e ->
                    Log.e(TAG, "❌ Error en flow: ${e.message}", e)
                    _uiState.update { it.copy(error = e.message, loading = false) }
                }
                .collect { incidents ->
                    Log.d(TAG, "📦 Flow emitió: ${incidents.size} incidentes")

                    _uiState.update { state ->
                        val filtered = applyFilters(incidents, state)

                        Log.d(TAG, "🔍 Después de filtros: ${filtered.size} incidentes")

                        state.copy(
                            incidents = incidents,
                            filteredIncidents = filtered,
                            loading = false
                        )
                    }
                }
        }
    }

    // ==========================================
    // BUSCAR INCIDENTES CERCANOS
    // ==========================================

    fun loadNearbyIncidents(lat: Double, lng: Double, radiusKm: Int = 5) {
        Log.d(TAG, "📍 Buscando cercanos: lat=$lat, lng=$lng, radius=$radiusKm km")

        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }

            repository.getNearbyIncidents(lat, lng, radiusKm)
                .onSuccess { incidents ->
                    Log.d(TAG, "✅ Cercanos encontrados: ${incidents.size}")

                    _uiState.update { state ->
                        state.copy(
                            incidents = incidents,
                            filteredIncidents = applyFilters(incidents, state),
                            loading = false
                        )
                    }
                }
                .onFailure { e ->
                    Log.e(TAG, "❌ Error buscando cercanos: ${e.message}", e)
                    _uiState.update { it.copy(error = e.message, loading = false) }
                }
        }
    }

    // ==========================================
    // APLICAR FILTROS
    // ==========================================

    private fun applyFilters(incidents: List<Incident>, state: DashboardUiState): List<Incident> {
        return incidents.filter { incident ->
            val matchesType = state.filterType == null || incident.type == state.filterType
            val matchesVerified = !state.showVerifiedOnly || incident.verified
            matchesType && matchesVerified
        }
    }

    fun filterByType(type: IncidentType?) {
        Log.d(TAG, "🏷️ Filtrando por tipo: ${type?.name ?: "Todos"}")

        _uiState.update { state ->
            state.copy(
                filterType = type,
                filteredIncidents = applyFilters(state.incidents, state.copy(filterType = type))
            )
        }
    }

    fun toggleVerifiedFilter() {
        Log.d(TAG, "✅ Alternando filtro de verificados")

        _uiState.update { state ->
            val newValue = !state.showVerifiedOnly
            state.copy(
                showVerifiedOnly = newValue,
                filteredIncidents = applyFilters(state.incidents, state.copy(showVerifiedOnly = newValue))
            )
        }
    }

    // ==========================================
    // SELECCIONAR INCIDENTE
    // ==========================================

    fun selectIncident(incident: Incident?) {
        Log.d(TAG, "👆 Incidente seleccionado: ${incident?.id ?: "ninguno"}")
        _uiState.update { it.copy(selectedIncident = incident) }
    }

    // ==========================================
    // UBICACIÓN DEL USUARIO
    // ==========================================

    fun updateUserLocation(location: Location) {
        Log.d(TAG, "📍 Ubicación actualizada: ${location.latitude}, ${location.longitude}")
        _uiState.update {
            it.copy(userLocation = LatLng(location.latitude, location.longitude))
        }
    }

    // ==========================================
    // CALCULAR DISTANCIA
    // ==========================================

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

    // ==========================================
    // CONFIRMAR INCIDENTE
    // ==========================================

    fun confirmIncident(incidentId: String) {
        Log.d(TAG, "✅ Confirmando incidente: $incidentId")

        viewModelScope.launch {
            repository.confirmIncident(incidentId)
                .onSuccess {
                    Log.d(TAG, "✅ Confirmación exitosa")
                    // El polling automático actualizará la UI
                }
                .onFailure { e ->
                    Log.e(TAG, "❌ Error confirmando: ${e.message}", e)
                    _uiState.update { it.copy(error = e.message) }
                }
        }
    }

    // ==========================================
    // ✅ NUEVO: DESCONFIRMAR INCIDENTE
    // ==========================================

    fun unconfirmIncident(incidentId: String) {
        Log.d(TAG, "❌ Desconfirmando incidente: $incidentId")

        viewModelScope.launch {
            repository.unconfirmIncident(incidentId)
                .onSuccess {
                    Log.d(TAG, "✅ Confirmación removida")
                    // El polling automático actualizará la UI
                }
                .onFailure { e ->
                    Log.e(TAG, "❌ Error desconfirmando: ${e.message}", e)
                    _uiState.update { it.copy(error = e.message) }
                }
        }
    }

    // ==========================================
    // CREAR INCIDENTE
    // ==========================================

    fun createIncident(incident: Incident, onSuccess: () -> Unit) {
        Log.d(TAG, "📝 Creando incidente: ${incident.type} - ${incident.category}")

        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }

            repository.createIncident(incident)
                .onSuccess { id ->
                    Log.d(TAG, "✅ Incidente creado: $id")
                    _uiState.update { it.copy(loading = false) }
                    // El polling automático agregará el nuevo incidente
                    onSuccess()
                }
                .onFailure { e ->
                    Log.e(TAG, "❌ Error creando: ${e.message}", e)
                    _uiState.update { it.copy(error = e.message, loading = false) }
                }
        }
    }

    // ==========================================
    // AGREGAR COMENTARIO
    // ==========================================

    fun addComment(incidentId: String, text: String) {
        viewModelScope.launch {
            repository.addComment(incidentId, text)
                .onSuccess {
                    // El polling actualizará
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message) }
                }
        }
    }

    // ==========================================
    // ELIMINAR INCIDENTE
    // ==========================================

    fun deleteIncident(incidentId: String) {
        viewModelScope.launch {
            repository.deleteIncident(incidentId)
                .onSuccess {
                    // El polling removerá el incidente
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message) }
                }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    // ==========================================
    // ✅ NUEVO: VERIFICAR SI EL USUARIO YA CONFIRMÓ
    // ==========================================

    fun hasUserConfirmed(incident: Incident): Boolean {
        val userId = _uiState.value.currentUserId ?: return false
        return incident.confirmedBy.contains(userId)
    }
}