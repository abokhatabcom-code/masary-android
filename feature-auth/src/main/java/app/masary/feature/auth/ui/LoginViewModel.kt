package app.masary.feature.auth.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.masary.core.datastore.SessionManager
import app.masary.feature.auth.domain.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class LoginViewModel(
    private val repository: AuthRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {
    private val _state = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            sessionManager.session.first()?.let { _state.value = LoginUiState.Success(it) }
        }
    }

    fun login(studentId: String, password: String) {
        if (_state.value == LoginUiState.Loading) return
        val normalizedId = studentId.trim()
        if (normalizedId.isEmpty()) {
            _state.value = LoginUiState.Error("أدخل رقم الطالب")
            return
        }
        if (password.isEmpty()) {
            _state.value = LoginUiState.Error("أدخل كلمة المرور")
            return
        }

        _state.value = LoginUiState.Loading
        viewModelScope.launch {
            repository.login(normalizedId, password.toCharArray())
                .onSuccess {
                    sessionManager.save(it)
                    _state.value = LoginUiState.Success(it)
                }
                .onFailure {
                    _state.value = LoginUiState.Error(it.message ?: "تعذر تسجيل الدخول")
                }
        }
    }

    fun logout() {
        if (_state.value == LoginUiState.Loading) return
        viewModelScope.launch {
            sessionManager.clear()
            _state.value = LoginUiState.Idle
        }
    }
}
