package app.masary.feature.auth.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.masary.core.datastore.SessionManager
import app.masary.feature.auth.domain.AuthRepository

@Composable
fun AuthRoute(repository: AuthRepository, sessionManager: SessionManager, deviceName: String) {
    val viewModel: LoginViewModel = viewModel(
        factory = LoginViewModelFactory(repository, sessionManager, deviceName),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    AuthScreen(state, viewModel::login, viewModel::logout)
}

private class LoginViewModelFactory(
    private val repository: AuthRepository,
    private val sessionManager: SessionManager,
    private val deviceName: String,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        LoginViewModel(repository, sessionManager, deviceName) as T
}
