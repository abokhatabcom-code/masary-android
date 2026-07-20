package app.masary.feature.auth.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.masary.core.datastore.SessionManager
import app.masary.core.models.auth.StudentSession
import app.masary.feature.auth.domain.AuthFailureException
import app.masary.feature.auth.domain.AuthFailureKind
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
    private val _state = MutableStateFlow<LoginUiState>(LoginUiState.Restoring)
    val state: StateFlow<LoginUiState> = _state.asStateFlow()
    private var logoutInProgress = false

    init {
        viewModelScope.launch { restoreSession() }
    }

    fun login(username: String, password: String) {
        if (_state.value == LoginUiState.Loading || _state.value == LoginUiState.Restoring) return
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
        if (logoutInProgress) return
        logoutInProgress = true
        viewModelScope.launch {
            try {
                sessionManager.readTokens()?.let { tokens ->
                    // Revocation is best-effort; local credentials are always removed below.
                    repository.logout(tokens)
                }
            } finally {
                runCatching { sessionManager.clear() }
                _state.value = LoginUiState.Idle
                logoutInProgress = false
            }
        }
    }

    private suspend fun restoreSession() {
        val existing = sessionManager.session.first()
        if (existing == null) {
            _state.value = LoginUiState.Idle
            return
        }

        val tokens = runCatching { sessionManager.readTokens() }.getOrNull()
        if (tokens == null) {
            runCatching { sessionManager.clear() }
            _state.value = LoginUiState.Idle
            return
        }

        if (!tokens.accessTokenNeedsRefresh()) {
            _state.value = LoginUiState.Success(existing)
            return
        }

        repository.refresh(tokens.refreshToken)
            .onSuccess { refreshed ->
                runCatching { sessionManager.updateTokens(refreshed) }
                    .onSuccess { _state.value = LoginUiState.Success(existing) }
                    .onFailure {
                        runCatching { sessionManager.clear() }
                        _state.value = LoginUiState.Idle
                    }
            }
            .onFailure { error ->
                val rejected = (error as? AuthFailureException)?.kind == AuthFailureKind.SESSION_REJECTED
                if (rejected) {
                    runCatching { sessionManager.clear() }
                    _state.value = LoginUiState.Idle
                } else {
                    // Keep the local identity available during a temporary outage. The next
                    // authenticated request can retry refresh when connectivity returns.
                    _state.value = LoginUiState.Success(existing)
                }
            }
    }
}
