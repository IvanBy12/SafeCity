package com.example.safecity.screens.detail

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.safecity.models.Incident
import com.example.safecity.repository.IncidentRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class IncidentDetailUiState(
    val incident: Incident? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val isOwner: Boolean = false,
    val userVoteStatus: String = "none",  // "none" | "true" | "false"
    // Compatibilidad
    val hasUserConfirmed: Boolean = false
)

class IncidentDetailViewModel(
    private val repository: IncidentRepository = IncidentRepository()
) : ViewModel() {

    private val TAG = "IncidentDetailVM"

    private val _uiState = MutableStateFlow(IncidentDetailUiState())
    val uiState = _uiState.asStateFlow()

    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

    fun loadIncident(incidentId: String) {
        Log.d(TAG, "Cargando incidente: $incidentId")

        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }

            repository.getIncidentsFlow()
                .collect { incidents ->
                    val incident = incidents.find { it.id == incidentId }

                    if (incident == null) {
                        _uiState.update { it.copy(loading = false, error = "Incidente no encontrado") }
                    } else {
                        _uiState.update {
                            it.copy(
                                incident = incident,
                                loading = false,
                                isOwner = incident.userId == currentUserId,
                                userVoteStatus = incident.userVoteStatus,
                                hasUserConfirmed = incident.userVoteStatus == "true"
                            )
                        }
                    }

                    return@collect
                }
        }
    }

    // ========================================
    // NUEVO: Votar verdadero
    // ========================================

    fun voteTrue() {
        val incidentId = _uiState.value.incident?.id ?: return
        Log.d(TAG, "Votando verdadero: $incidentId")

        viewModelScope.launch {
            repository.voteTrue(incidentId)
                .onSuccess { loadIncident(incidentId) }
                .onFailure { e ->
                    Log.e(TAG, "Error: ${e.message}", e)
                    _uiState.update { it.copy(error = e.message) }
                }
        }
    }

    // ========================================
    // NUEVO: Votar falso
    // ========================================

    fun voteFalse() {
        val incidentId = _uiState.value.incident?.id ?: return
        Log.d(TAG, "Votando falso: $incidentId")

        viewModelScope.launch {
            repository.voteFalse(incidentId)
                .onSuccess { loadIncident(incidentId) }
                .onFailure { e ->
                    Log.e(TAG, "Error: ${e.message}", e)
                    _uiState.update { it.copy(error = e.message) }
                }
        }
    }

    // ========================================
    // NUEVO: Quitar voto
    // ========================================

    fun removeVote() {
        val incidentId = _uiState.value.incident?.id ?: return
        Log.d(TAG, "Removiendo voto: $incidentId")

        viewModelScope.launch {
            repository.removeVote(incidentId)
                .onSuccess { loadIncident(incidentId) }
                .onFailure { e ->
                    Log.e(TAG, "Error: ${e.message}", e)
                    _uiState.update { it.copy(error = e.message) }
                }
        }
    }

    // ========================================
    // COMPATIBILIDAD
    // ========================================

    fun confirmIncident() = voteTrue()
    fun unconfirmIncident() = removeVote()

    fun deleteIncident() {
        val incidentId = _uiState.value.incident?.id ?: return
        if (!_uiState.value.isOwner) return

        viewModelScope.launch {
            repository.deleteIncident(incidentId)
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message) }
                }
        }
    }
}