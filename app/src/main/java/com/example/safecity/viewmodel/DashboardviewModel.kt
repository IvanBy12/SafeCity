package com.example.safecity.viewmodel

import android.location.Location
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.safecity.models.Comment
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
    val loading: Boolean = false,
    val error: String? = null,
    val currentUserId: String? = null,
    // ========================================
    // COMENTARIOS
    // ========================================
    val comments: List<Comment> = emptyList(),
    val commentsLoading: Boolean = false,
    val commentSending: Boolean = false
)

class DashboardViewModel(
    private val repository: IncidentRepository = IncidentRepository()
) : ViewModel() {

    private val TAG = "DashboardViewModel"

    private val _uiState = MutableStateFlow(DashboardUiState(
        currentUserId = FirebaseAuth.getInstance().currentUser?.uid
    ))
    val uiState = _uiState.asStateFlow()

    init {
        Log.d(TAG, "DashboardViewModel inicializado")
        observeIncidents()
    }

    private fun observeIncidents() {
        viewModelScope.launch {
            repository.getIncidentsFlow()
                .catch { e ->
                    Log.e(TAG, "Error en flow: ${e.message}", e)
                    _uiState.update { it.copy(error = e.message, loading = false) }
                }
                .collect { incidents ->
                    Log.d(TAG, "Flow emitio: ${incidents.size} incidentes")
                    _uiState.update { state ->
                        val filtered = applyFilters(incidents, state)
                        state.copy(
                            incidents = incidents,
                            filteredIncidents = filtered,
                            loading = false
                        )
                    }
                }
        }
    }

    fun loadNearbyIncidents(lat: Double, lng: Double, radiusKm: Int = 5) {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            repository.getNearbyIncidents(lat, lng, radiusKm)
                .onSuccess { incidents ->
                    _uiState.update { state ->
                        state.copy(
                            incidents = incidents,
                            filteredIncidents = applyFilters(incidents, state),
                            loading = false
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message, loading = false) }
                }
        }
    }

    private fun applyFilters(incidents: List<Incident>, state: DashboardUiState): List<Incident> {
        return incidents.filter { incident ->
            val matchesType = state.filterType == null || incident.type == state.filterType
            val matchesVerified = !state.showVerifiedOnly || incident.verified
            val notFlagged = !incident.flaggedFalse
            matchesType && matchesVerified && notFlagged
        }
    }

    fun filterByType(type: IncidentType?) {
        _uiState.update { state ->
            state.copy(
                filterType = type,
                filteredIncidents = applyFilters(state.incidents, state.copy(filterType = type))
            )
        }
    }

    fun toggleVerifiedFilter() {
        _uiState.update { state ->
            val newValue = !state.showVerifiedOnly
            state.copy(
                showVerifiedOnly = newValue,
                filteredIncidents = applyFilters(state.incidents, state.copy(showVerifiedOnly = newValue))
            )
        }
    }

    fun selectIncident(incident: Incident?) {
        _uiState.update { it.copy(selectedIncident = incident, comments = emptyList()) }
        // Auto-cargar comentarios al seleccionar
        if (incident != null) {
            loadComments(incident.id)
        }
    }

    fun updateUserLocation(location: Location) {
        _uiState.update {
            it.copy(userLocation = LatLng(location.latitude, location.longitude))
        }
    }

    fun calculateDistance(from: LatLng, to: GeoPoint): String {
        val results = FloatArray(1)
        Location.distanceBetween(from.latitude, from.longitude, to.latitude, to.longitude, results)
        val meters = results[0]
        return when {
            meters < 1000 -> "${meters.toInt()} m"
            else -> String.format("%.1f km", meters / 1000)
        }
    }

    // ========================================
    // COMENTARIOS
    // ========================================

    fun loadComments(incidentId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(commentsLoading = true) }
            repository.getIncidentComments(incidentId)
                .onSuccess { comments ->
                    Log.d(TAG, "Comentarios cargados: ${comments.size}")
                    _uiState.update { it.copy(comments = comments, commentsLoading = false) }
                }
                .onFailure { e ->
                    Log.e(TAG, "Error cargando comentarios: ${e.message}", e)
                    _uiState.update { it.copy(commentsLoading = false) }
                }
        }
    }

    fun sendComment(incidentId: String, text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(commentSending = true) }
            repository.addComment(incidentId, text)
                .onSuccess {
                    Log.d(TAG, "Comentario enviado, recargando...")
                    _uiState.update { it.copy(commentSending = false) }
                    // Recargar comentarios después de enviar
                    loadComments(incidentId)
                }
                .onFailure { e ->
                    Log.e(TAG, "Error enviando comentario: ${e.message}", e)
                    _uiState.update { it.copy(commentSending = false, error = e.message) }
                }
        }
    }

    // ========================================
    // VOTAR VERDADERO
    // ========================================

    fun voteTrue(incidentId: String) {
        Log.d(TAG, "Votando verdadero: $incidentId")
        viewModelScope.launch {
            repository.voteTrue(incidentId)
                .onSuccess { Log.d(TAG, "Voto verdadero exitoso") }
                .onFailure { e ->
                    Log.e(TAG, "Error votando verdadero: ${e.message}", e)
                    _uiState.update { it.copy(error = e.message) }
                }
        }
    }

    // ========================================
    // VOTAR FALSO
    // ========================================

    fun voteFalse(incidentId: String) {
        Log.d(TAG, "Votando falso: $incidentId")
        viewModelScope.launch {
            repository.voteFalse(incidentId)
                .onSuccess { Log.d(TAG, "Voto falso exitoso") }
                .onFailure { e ->
                    Log.e(TAG, "Error votando falso: ${e.message}", e)
                    _uiState.update { it.copy(error = e.message) }
                }
        }
    }

    // ========================================
    // QUITAR VOTO
    // ========================================

    fun removeVote(incidentId: String) {
        Log.d(TAG, "Removiendo voto: $incidentId")
        viewModelScope.launch {
            repository.removeVote(incidentId)
                .onSuccess { Log.d(TAG, "Voto removido exitosamente") }
                .onFailure { e ->
                    Log.e(TAG, "Error removiendo voto: ${e.message}", e)
                    _uiState.update { it.copy(error = e.message) }
                }
        }
    }

    // ========================================
    // COMPATIBILIDAD
    // ========================================

    fun confirmIncident(incidentId: String) = voteTrue(incidentId)
    fun unconfirmIncident(incidentId: String) = removeVote(incidentId)

    // ========================================
    // HELPERS DE ESTADO DEL VOTO
    // ========================================

    fun getUserVoteStatus(incident: Incident): String = incident.userVoteStatus
    fun hasUserConfirmed(incident: Incident): Boolean = incident.userVoteStatus == "true"
    fun hasUserFlagged(incident: Incident): Boolean = incident.userVoteStatus == "false"
    fun isOwner(incident: Incident): Boolean = incident.userId == _uiState.value.currentUserId

    // ========================================
    // CREAR INCIDENTE (con fotos opcionales)
    // ========================================

    fun createIncident(incident: Incident, photoUrls: List<String> = emptyList(), onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            repository.createIncident(incident, photoUrls)
                .onSuccess {
                    _uiState.update { it.copy(loading = false) }
                    onSuccess()
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message, loading = false) }
                }
        }
    }

    fun addComment(incidentId: String, text: String) {
        sendComment(incidentId, text)
    }

    fun deleteIncident(incidentId: String) {
        viewModelScope.launch {
            repository.deleteIncident(incidentId)
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message) }
                }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}