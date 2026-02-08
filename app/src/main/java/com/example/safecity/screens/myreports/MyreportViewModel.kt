package com.example.safecity.screens.myreports

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

data class MyReportsUiState(
    val reports: List<Incident> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null
)

class MyReportsViewModel(
    private val repository: IncidentRepository = IncidentRepository()
) : ViewModel() {

    private val TAG = "MyReportsViewModel"

    private val _uiState = MutableStateFlow(MyReportsUiState())
    val uiState = _uiState.asStateFlow()

    fun loadMyReports() {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

        if (currentUserId == null) {
            _uiState.update { it.copy(error = "No autenticado") }
            return
        }

        Log.d(TAG, "📋 Cargando reportes del usuario: $currentUserId")

        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }

            // Recolectar una vez del flow
            repository.getIncidentsFlow()
                .collect { allIncidents ->
                    // Filtrar solo los incidentes del usuario actual
                    val myIncidents = allIncidents.filter { it.userId == currentUserId }

                    Log.d(TAG, "✅ Reportes del usuario: ${myIncidents.size} de ${allIncidents.size} totales")

                    _uiState.update {
                        it.copy(
                            reports = myIncidents.sortedByDescending { incident -> incident.timestamp },
                            loading = false
                        )
                    }

                    // Solo recolectar una vez
                    return@collect
                }
        }
    }
}
>>>>>>> Stashed changes
