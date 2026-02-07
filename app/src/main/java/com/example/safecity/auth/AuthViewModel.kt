package com.example.safecity.auth


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val loading: Boolean = false,
    val error: String? = null
)



class AuthViewModel(
    private val repo: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _ui = MutableStateFlow(AuthUiState())
    val ui = _ui.asStateFlow()

    fun isLoggedIn(): Boolean = repo.currentUser != null

    fun login(email: String, password: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _ui.value = AuthUiState(loading = true)
            try {
                repo.login(email, password)   // aquí ya refresca el token
                _ui.value = AuthUiState()
                onSuccess()
            } catch (e: Exception) {
                _ui.value = AuthUiState(
                    loading = false,
                    error = e.message ?: "Error iniciando sesión"
                )
            }
        }
    }

    fun register(email: String, password: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _ui.value = AuthUiState(loading = true)
            try {
                repo.register(email, password) // aquí ya refresca el token
                _ui.value = AuthUiState()
                onSuccess()
            } catch (e: Exception) {
                _ui.value = AuthUiState(
                    loading = false,
                    error = e.message ?: "Error registrando"
                )
            }
        }
    }

    fun logout(onDone: () -> Unit) {
        repo.logout()
        onDone()
    }

    fun clearError() {
        _ui.value = _ui.value.copy(error = null)
    }
}