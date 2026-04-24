package com.example.safecity.viewmodel

import android.app.Application
import android.location.Location
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.safecity.models.Comment
import com.example.safecity.models.Incident
import com.example.safecity.models.IncidentType
import com.example.safecity.network.DeviceSyncManager
import com.example.safecity.repository.IncidentRepository
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.GeoPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
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
    val comments: List<Comment> = emptyList(),
    val commentsLoading: Boolean = false,
    val commentSending: Boolean = false
)

class DashboardViewModel(
    application: Application,
    private val repository: IncidentRepository = IncidentRepository()
) : AndroidViewModel(application) {

    private val TAG = "DashboardViewModel"
    private val context = application.applicationContext

    private val _uiState = MutableStateFlow(
        DashboardUiState(currentUserId = FirebaseAuth.getInstance().currentUser?.uid)
    )
    val uiState = _uiState.asStateFlow()

    /**
     * ID estable del dispositivo. No cambia entre reinicios de la app.
     * Lazy para que no crashee si Settings.Secure falla.
     */
    private val deviceId: String by lazy {
        try {
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            ) ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }

    // ==========================================
    // FACTORY — necesario porque el constructor
    // recibe Application. Sin esto Compose crashea.
    // ==========================================
    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(
                modelClass: Class<T>,
                extras: CreationExtras
            ): T {
                val application = checkNotNull(extras[APPLICATION_KEY]) {
                    "DashboardViewModel requiere Application en extras"
                }
                return DashboardViewModel(application) as T
            }
        }
    }

    init {
        Log.d(TAG, "DashboardViewModel inicializado (deviceId=$deviceId)")
        _uiState.value = _uiState.value.copy(loading = true)
        observeIncidents()
        registerDeviceWithBackend()
        viewModelScope.launch { repository.refreshIncidentsNow() }
    }

    // ==========================================
    // OBSERVAR INCIDENTES (polling)
    // ==========================================
    private fun observeIncidents() {
        viewModelScope.launch {
            repository.getIncidentsFlow()
                .catch { e ->
                    Log.e(TAG, "Error en flow: ${e.message}", e)
                    _uiState.value = _uiState.value.copy(error = e.message, loading = false)
                }
                .collect { incidents ->
                    _uiState.value = _uiState.value.let { state ->
                        val updatedSelected = state.selectedIncident?.let { selected ->
                            incidents.find { it.id == selected.id }
                        }

                        state.copy(
                            incidents = incidents,
                            filteredIncidents = applyFilters(incidents, state),
                            selectedIncident = updatedSelected,
                            loading = false
                        )
                    }
                }
        }
    }

    // ==========================================
    // REGISTRO DE DISPOSITIVO EN BACKEND
    // ==========================================
    private fun registerDeviceWithBackend() {
        viewModelScope.launch {
            val synced = DeviceSyncManager.syncDevice(context)
            if (!synced) {
                Log.w(TAG, "No se pudo registrar dispositivo en arranque")
            }
        }
    }

    // ==========================================
    // ACTUALIZAR UBICACIÓN
    // Guarda en UI state y envía al backend para FCM de proximidad
    // ==========================================
    fun updateUserLocation(location: Location) {
        _uiState.value = _uiState.value.copy(
            userLocation = LatLng(location.latitude, location.longitude)
        )
        sendLocationToBackend(location)
    }

    private fun sendLocationToBackend(location: Location) {
        viewModelScope.launch {
            val synced = DeviceSyncManager.syncLocation(
                context = context,
                latitude = location.latitude,
                longitude = location.longitude
            )
            if (!synced) {
                Log.w(TAG, "No se pudo enviar ubicación al backend")
            }
        }
    }

    // ==========================================
    // FILTROS
    // ==========================================
    private fun applyFilters(incidents: List<Incident>, state: DashboardUiState): List<Incident> {
        return incidents.filter { incident ->
            val matchesType = state.filterType == null || incident.type == state.filterType
            val matchesVerified = !state.showVerifiedOnly || incident.verified
            val notFlagged = !incident.flaggedFalse
            matchesType && matchesVerified && notFlagged
        }
    }

    fun filterByType(type: IncidentType?) {
        _uiState.value = _uiState.value.let { state ->
            state.copy(
                filterType = type,
                filteredIncidents = applyFilters(state.incidents, state.copy(filterType = type))
            )
        }
    }

    fun toggleVerifiedFilter() {
        _uiState.value = _uiState.value.let { state ->
            val newValue = !state.showVerifiedOnly
            state.copy(
                showVerifiedOnly = newValue,
                filteredIncidents = applyFilters(state.incidents, state.copy(showVerifiedOnly = newValue))
            )
        }
    }

    fun selectIncident(incident: Incident?) {
        _uiState.value = _uiState.value.copy(selectedIncident = incident, comments = emptyList())
        if (incident != null) {
            loadComments(incident.id)
        }
    }

    fun loadNearbyIncidents(lat: Double, lng: Double, radiusKm: Int = 5) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true)
            repository.getNearbyIncidents(lat, lng, radiusKm)
                .onSuccess { incidents ->
                    _uiState.value = _uiState.value.let { state ->
                        state.copy(
                            incidents = incidents,
                            filteredIncidents = applyFilters(incidents, state),
                            loading = false
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(error = e.message, loading = false)
                }
        }
    }

    fun calculateDistance(from: LatLng, to: GeoPoint): String {
        val results = FloatArray(1)
        Location.distanceBetween(
            from.latitude, from.longitude,
            to.latitude, to.longitude,
            results
        )
        val meters = results[0]
        return if (meters < 1000) "${meters.toInt()} m"
        else String.format("%.1f km", meters / 1000)
    }

    // ==========================================
    // COMENTARIOS
    // ==========================================
    fun loadComments(incidentId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(commentsLoading = true)
            repository.getIncidentComments(incidentId)
                .onSuccess { comments ->
                    Log.d(TAG, "Comentarios cargados: ${comments.size}")
                    _uiState.value = _uiState.value.copy(
                        comments = comments,
                        commentsLoading = false
                    )
                }
                .onFailure { e ->
                    Log.e(TAG, "Error cargando comentarios: ${e.message}", e)
                    _uiState.value = _uiState.value.copy(commentsLoading = false)
                }
        }
    }

    fun sendComment(incidentId: String, text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(commentSending = true)
            repository.addComment(incidentId, text)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(commentSending = false)
                    loadComments(incidentId)
                }
                .onFailure { e ->
                    Log.e(TAG, "Error enviando comentario: ${e.message}", e)
                    _uiState.value = _uiState.value.copy(commentSending = false, error = e.message)
                }
        }
    }

    // ==========================================
    // VOTACIÓN
    // ==========================================
    fun voteTrue(incidentId: String) {
        viewModelScope.launch {
            repository.voteTrue(incidentId)
                .onFailure { e -> _uiState.value = _uiState.value.copy(error = e.message) }
        }
    }

    fun voteFalse(incidentId: String) {
        viewModelScope.launch {
            repository.voteFalse(incidentId)
                .onFailure { e -> _uiState.value = _uiState.value.copy(error = e.message) }
        }
    }

    fun removeVote(incidentId: String) {
        viewModelScope.launch {
            repository.removeVote(incidentId)
                .onFailure { e -> _uiState.value = _uiState.value.copy(error = e.message) }
        }
    }

    fun confirmIncident(incidentId: String) = voteTrue(incidentId)
    fun unconfirmIncident(incidentId: String) = removeVote(incidentId)

    // ==========================================
    // HELPERS DE ESTADO DEL VOTO
    // ==========================================
    fun getUserVoteStatus(incident: Incident): String = incident.userVoteStatus
    fun hasUserConfirmed(incident: Incident): Boolean = incident.userVoteStatus == "true"
    fun hasUserFlagged(incident: Incident): Boolean = incident.userVoteStatus == "false"
    fun isOwner(incident: Incident): Boolean = incident.userId == _uiState.value.currentUserId

    // ==========================================
    // CREAR INCIDENTE
    // ==========================================
    fun createIncident(
        incident: Incident,
        photoUrls: List<String> = emptyList(),
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true)
            repository.createIncident(incident, photoUrls)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(loading = false)
                    onSuccess()
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(error = e.message, loading = false)
                    onFailure(e.message ?: "Error desconocido")
                }
        }
    }

    fun addComment(incidentId: String, text: String) = sendComment(incidentId, text)

    fun deleteIncident(incidentId: String) {
        viewModelScope.launch {
            repository.deleteIncident(incidentId)
                .onFailure { e -> _uiState.value = _uiState.value.copy(error = e.message) }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
