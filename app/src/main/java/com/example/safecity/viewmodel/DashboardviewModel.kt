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
import com.example.safecity.network.ApiClient
import com.example.safecity.network.DeviceRegistrationRequest
import com.example.safecity.network.LocationUpdateRequest
import com.example.safecity.network.TokenStore
import com.example.safecity.repository.IncidentRepository
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

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
        observeIncidents()
        registerDeviceWithBackend()
    }

    // ==========================================
    // OBSERVAR INCIDENTES (polling)
    // ==========================================
    private fun observeIncidents() {
        viewModelScope.launch {
            repository.getIncidentsFlow()
                .catch { e ->
                    Log.e(TAG, "Error en flow: ${e.message}", e)
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

    // ==========================================
    // REGISTRO DE DISPOSITIVO EN BACKEND
    // ==========================================
    private fun registerDeviceWithBackend() {
        viewModelScope.launch {
            try {
                val token = TokenStore.get() ?: TokenStore.refresh() ?: run {
                    Log.w(TAG, "Sin token para registrar dispositivo")
                    return@launch
                }
                val fcmToken = FirebaseMessaging.getInstance().token.await()

                ApiClient.api.registerDevice(
                    "Bearer $token",
                    DeviceRegistrationRequest(
                        deviceId = deviceId,
                        platform = "android",
                        fcmToken = fcmToken
                    )
                )
                Log.d(TAG, "Dispositivo registrado en backend OK")
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo registrar dispositivo: ${e.message}")
            }
        }
    }

    // ==========================================
    // ACTUALIZAR UBICACIÓN
    // Guarda en UI state Y envía al backend para FCM de proximidad
    // ==========================================
    fun updateUserLocation(location: Location) {
        _uiState.update {
            it.copy(userLocation = LatLng(location.latitude, location.longitude))
        }
        sendLocationToBackend(location)
    }

    private fun sendLocationToBackend(location: Location) {
        viewModelScope.launch {
            try {
                val token = TokenStore.get() ?: TokenStore.refresh() ?: return@launch
                ApiClient.api.updateLocation(
                    "Bearer $token",
                    LocationUpdateRequest(
                        deviceId = deviceId,
                        latitude = location.latitude,
                        longitude = location.longitude
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo enviar ubicación: ${e.message}")
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
        if (incident != null) {
            loadComments(incident.id)
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
                .onFailure { e -> _uiState.update { it.copy(error = e.message, loading = false) } }
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
                    _uiState.update { it.copy(commentSending = false) }
                    loadComments(incidentId)
                }
                .onFailure { e ->
                    Log.e(TAG, "Error enviando comentario: ${e.message}", e)
                    _uiState.update { it.copy(commentSending = false, error = e.message) }
                }
        }
    }

    // ==========================================
    // VOTACIÓN
    // ==========================================
    fun voteTrue(incidentId: String) {
        viewModelScope.launch {
            repository.voteTrue(incidentId)
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun voteFalse(incidentId: String) {
        viewModelScope.launch {
            repository.voteFalse(incidentId)
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun removeVote(incidentId: String) {
        viewModelScope.launch {
            repository.removeVote(incidentId)
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
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
        onSuccess: () -> Unit
    ) {
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

    fun addComment(incidentId: String, text: String) = sendComment(incidentId, text)

    fun deleteIncident(incidentId: String) {
        viewModelScope.launch {
            repository.deleteIncident(incidentId)
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}