package com.example.safecity.screens.detail

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.safecity.models.Comment
import com.example.safecity.models.Incident
import com.example.safecity.repository.IncidentRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

data class IncidentDetailUiState(
    val incident: Incident? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val isOwner: Boolean = false,
    val userVoteStatus: String = "none",
    val comments: List<Comment> = emptyList(),
    val commentsLoading: Boolean = false,
    val commentSending: Boolean = false
)

class IncidentDetailViewModel(
    private val repository: IncidentRepository = IncidentRepository()
) : ViewModel() {

    private val TAG = "IncidentDetailVM"
    private val auth = FirebaseAuth.getInstance()

    private val _uiState = MutableStateFlow(IncidentDetailUiState())
    val uiState = _uiState.asStateFlow()

    private var currentIncidentId: String = ""
    private var incidentObserverJob: Job? = null

    fun loadIncident(incidentId: String) {
        currentIncidentId = incidentId
        _uiState.update { it.copy(loading = true, error = null) }

        incidentObserverJob?.cancel()
        incidentObserverJob = viewModelScope.launch {
            repository.getIncidentsFlow().collect { incidents ->
                val incident = incidents.find { it.id == incidentId }
                val currentUserId = auth.currentUser?.uid ?: ""

                if (incident != null) {
                    _uiState.update {
                        it.copy(
                            incident = incident,
                            loading = false,
                            isOwner = incident.userId == currentUserId,
                            userVoteStatus = incident.userVoteStatus,
                            error = null
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            loading = false,
                            incident = null,
                            error = if (incidents.isEmpty()) null else "Incidente no encontrado"
                        )
                    }
                }
            }
        }

        viewModelScope.launch {
            repository.refreshIncidentsNow()
        }
    }

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

    fun voteTrue() {
        if (currentIncidentId.isBlank()) return
        viewModelScope.launch {
            repository.voteTrue(currentIncidentId)
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun voteFalse() {
        if (currentIncidentId.isBlank()) return
        viewModelScope.launch {
            repository.voteFalse(currentIncidentId)
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun removeVote() {
        if (currentIncidentId.isBlank()) return
        viewModelScope.launch {
            repository.removeVote(currentIncidentId)
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun deleteIncident(onSuccess: () -> Unit = {}) {
        if (currentIncidentId.isBlank()) return
        viewModelScope.launch {
            repository.deleteIncident(currentIncidentId)
                .onSuccess { onSuccess() }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    override fun onCleared() {
        incidentObserverJob?.cancel()
        super.onCleared()
    }
}