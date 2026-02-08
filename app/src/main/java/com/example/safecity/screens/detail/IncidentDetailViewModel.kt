package com.example.safecity.screens.detail

<<<<<<< Updated upstream
=======
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
        Log.d(TAG, "🔍 Cargando incidente: $incidentId")

        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }

            // Obtener incidente del flow
            repository.getIncidentsFlow()
                .collect { incidents ->
                    val incident = incidents.find { it.id == incidentId }

                    if (incident == null) {
                        Log.e(TAG, "❌ Incidente no encontrado: $incidentId")
                        _uiState.update {
                            it.copy(
                                loading = false,
                                error = "Incidente no encontrado"
                            )
                        }
                    } else {
                        Log.d(TAG, "✅ Incidente cargado: ${incident.category}")

                        _uiState.update {
                            it.copy(
                                incident = incident,
                                loading = false,
                                isOwner = incident.userId == currentUserId,
                                hasUserConfirmed = incident.confirmedBy.contains(currentUserId)
                            )
                        }
                    }

                    // Solo recolectar una vez
                    return@collect
                }
        }
    }

    fun confirmIncident() {
        val incidentId = _uiState.value.incident?.id ?: return

        Log.d(TAG, "✅ Confirmando incidente: $incidentId")

        viewModelScope.launch {
            repository.confirmIncident(incidentId)
                .onSuccess {
                    Log.d(TAG, "✅ Confirmación exitosa")
                    // Recargar para actualizar estado
                    loadIncident(incidentId)
                }
                .onFailure { e ->
                    Log.e(TAG, "❌ Error confirmando: ${e.message}", e)
                    _uiState.update { it.copy(error = e.message) }
                }
        }
    }

    fun unconfirmIncident() {
        val incidentId = _uiState.value.incident?.id ?: return

        Log.d(TAG, "❌ Desconfirmando incidente: $incidentId")

        viewModelScope.launch {
            repository.unconfirmIncident(incidentId)
                .onSuccess {
                    Log.d(TAG, "✅ Confirmación removida")
                    // Recargar para actualizar estado
                    loadIncident(incidentId)
                }
                .onFailure { e ->
                    Log.e(TAG, "❌ Error desconfirmando: ${e.message}", e)
                    _uiState.update { it.copy(error = e.message) }
                }
        }
    }

    fun deleteIncident() {
        val incidentId = _uiState.value.incident?.id ?: return

        if (!_uiState.value.isOwner) {
            Log.e(TAG, "❌ No autorizado para eliminar")
            return
        }

        Log.d(TAG, "🗑️ Eliminando incidente: $incidentId")

        viewModelScope.launch {
            repository.deleteIncident(incidentId)
                .onSuccess {
                    Log.d(TAG, "✅ Incidente eliminado")
                }
                .onFailure { e ->
                    Log.e(TAG, "❌ Error eliminando: ${e.message}", e)
                    _uiState.update { it.copy(error = e.message) }
                }
        }
    }
}
>>>>>>> Stashed changes
