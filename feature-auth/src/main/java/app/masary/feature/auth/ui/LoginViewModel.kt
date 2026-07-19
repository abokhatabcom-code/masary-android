package app.masary.feature.auth.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.masary.core.datastore.SessionManager
import app.masary.core.models.auth.StudentSession
import app.masary.feature.auth.domain.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class LoginViewModel(
    private val repository: AuthRepository,
    private val sessionManager: SessionManager,
    private val deviceName: String,
) : ViewModel() {
    private val _state = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            sessionManager.session.first()?.let { _state.value = LoginUiState.Success(it) }
        }
    }

    fun login(username: String, password: String) {
        if (_state.value == LoginUiState.Loading) return
        val normalizedUsername = username.trim()
        if (normalizedUsername.isEmpty()) {
            _state.value = LoginUiState.Error("أدخل اسم المستخدم")
            return
        }
        if (password.isEmpty()) {
            _state.value = LoginUiState.Error("أدخل كلمة المرور")
            return
        }

        _state.value = LoginUiState.Loading
        viewModelScope.launch {
            repository.login(normalizedUsername, password.toCharArray(), deviceName)
                .onSuccess { authenticated ->
                    runCatching { sessionManager.save(authenticated) }
                        .onSuccess {
                            val student = authenticated.student
                            _state.value = LoginUiState.Success(
                                StudentSession(student.id, student.username, student.displayName),
                            )
                        }
                        .onFailure { _state.value = LoginUiState.Error("تعذر حفظ الجلسة بأمان") }
                }
                .onFailure {
                    _state.value = LoginUiState.Error(it.message ?: "تعذر تسجيل الدخول")
                }
        }
    }

    fun logout() {
        if (_state.value == LoginUiState.Loading) return
        viewModelScope.launch {
            runCatching { sessionManager.clear() }
            _state.value = LoginUiState.Idle
        }
    }
}
