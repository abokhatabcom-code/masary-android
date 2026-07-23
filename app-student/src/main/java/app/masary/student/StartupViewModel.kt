package app.masary.student

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.masary.core.datastore.OnboardingStore
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

sealed interface StartupState {
    data object Preparing : StartupState
    data object NeedsOnboarding : StartupState
    data object NeedsAuthentication : StartupState
    data class Authenticated(val session: StudentSession) : StartupState
    data object RecoverableError : StartupState
}

class StartupViewModel(
    private val onboardingStore: OnboardingStore,
    private val sessionManager: SessionManager,
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<StartupState>(StartupState.Preparing)
    val state: StateFlow<StartupState> = _state.asStateFlow()
    private var preparationStarted = false

    init {
        prepare()
    }

    fun retry() = prepare()

    fun completeOnboarding() {
        viewModelScope.launch {
            try {
                onboardingStore.markCompleted()
                resolveSession()
            } catch (_: Throwable) {
                _state.value = StartupState.RecoverableError
            }
        }
    }

    /** Internal entry point for a future settings action that deliberately replays onboarding. */
    fun requestOnboardingReplay() {
        viewModelScope.launch {
            runCatching { onboardingStore.reset() }
                .onSuccess { _state.value = StartupState.NeedsOnboarding }
                .onFailure { _state.value = StartupState.RecoverableError }
        }
    }

    fun authenticated(session: StudentSession) {
        _state.value = StartupState.Authenticated(session)
    }

    fun logout() {
        _state.value = StartupState.Preparing
        viewModelScope.launch {
            val tokens = runCatching { sessionManager.readTokens() }.getOrNull()
            runCatching { sessionManager.clear() }
            _state.value = StartupState.NeedsAuthentication
            if (tokens != null) authRepository.logout(tokens)
        }
    }

    /** Clears the UI/session immediately; deferred notification worker completes server unregister + logout. */
    fun logoutLocally() {
        _state.value = StartupState.Preparing
        viewModelScope.launch {
            runCatching { sessionManager.clear() }
            _state.value = StartupState.NeedsAuthentication
        }
    }

    /** Keeps the authenticated session intact and exposes a visible retry screen. */
    fun logoutPreparationFailed() {
        _state.value = StartupState.RecoverableError
    }

    private fun prepare() {
        if (_state.value == StartupState.Preparing && preparationStarted) return
        _state.value = StartupState.Preparing
        preparationStarted = true
        viewModelScope.launch {
            try {
                if (!onboardingStore.isCompleted.first()) {
                    _state.value = StartupState.NeedsOnboarding
                } else {
                    resolveSession()
                }
            } catch (_: Throwable) {
                _state.value = StartupState.RecoverableError
            } finally {
                preparationStarted = false
            }
        }
    }

    private suspend fun resolveSession() {
        val session = sessionManager.session.first()
        if (session == null) {
            _state.value = StartupState.NeedsAuthentication
            return
        }
        val tokens = sessionManager.readTokens()
        if (tokens == null) {
            clearRejectedSession()
            return
        }

        authRepository.validateSession(tokens)
            .onSuccess { validated ->
                if (validated != tokens) sessionManager.updateTokens(validated)
                _state.value = StartupState.Authenticated(session)
            }
            .onFailure { error ->
                val failure = error as? AuthFailureException
                if (failure?.kind == AuthFailureKind.SESSION_REJECTED) {
                    clearRejectedSession()
                } else {
                    _state.value = StartupState.RecoverableError
                }
            }
    }

    private suspend fun clearRejectedSession() {
        runCatching { sessionManager.clear() }
        _state.value = StartupState.NeedsAuthentication
    }
}

class StartupViewModelFactory(
    private val onboardingStore: OnboardingStore,
    private val sessionManager: SessionManager,
    private val authRepository: AuthRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(StartupViewModel::class.java))
        return StartupViewModel(onboardingStore, sessionManager, authRepository) as T
    }
}
