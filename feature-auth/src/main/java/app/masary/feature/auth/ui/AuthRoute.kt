package app.masary.feature.auth.ui

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import app.masary.core.datastore.SessionManager
import app.masary.core.models.auth.StudentSession
import app.masary.feature.auth.domain.AuthRepository
import app.masary.feature.auth.domain.RegistrationRepository

@Composable
fun AuthRoute(
    repository: AuthRepository,
    registrationRepository: RegistrationRepository,
    sessionManager: SessionManager,
    deviceName: String,
    onAuthenticated: (StudentSession) -> Unit = {},
    authenticatedContent: (@Composable (StudentSession, () -> Unit) -> Unit)? = null,
) {
    val login: LoginViewModel = viewModel(factory = LoginFactory(repository, sessionManager, deviceName))
    val state by login.state.collectAsStateWithLifecycle()
    var registering by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state) { (state as? LoginUiState.Success)?.session?.let(onAuthenticated) }

    if (registering) {
        val registration: RegistrationViewModel = viewModel(
            factory = RegistrationFactory(
                registrationRepository,
                sessionManager,
                deviceName,
                done = { registering = false; onAuthenticated(it) },
                cancelled = { registering = false },
            ),
        )
        val registrationState by registration.state.collectAsStateWithLifecycle()
        RegistrationScreen(
            registrationState,
            registration::updateDraft,
            registration::nextAccount,
            registration::selectCity,
            registration::nextAcademic,
            registration::submit,
            registration::back,
            registration::requestCancel,
            registration::dismissCancel,
            registration::confirmCancel,
            registration::retryCities,
            registration::retryGrades,
            registration::retrySchools,
        )
    } else if (state is LoginUiState.Success && authenticatedContent != null) {
        authenticatedContent((state as LoginUiState.Success).session, login::logout)
    } else {
        AuthScreen(state, login::login, login::logout) { registering = true }
    }
}

private class LoginFactory(private val repository: AuthRepository, private val sessions: SessionManager, private val device: String) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = LoginViewModel(repository, sessions, device) as T
}

private class RegistrationFactory(
    private val repository: RegistrationRepository,
    private val sessions: SessionManager,
    private val device: String,
    private val done: (StudentSession) -> Unit,
    private val cancelled: () -> Unit,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
        RegistrationViewModel(extras.createSavedStateHandle(), repository, sessions, device, done, cancelled) as T
}
