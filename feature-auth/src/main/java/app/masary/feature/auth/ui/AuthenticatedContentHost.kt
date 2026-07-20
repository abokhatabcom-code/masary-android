package app.masary.feature.auth.ui

import androidx.compose.runtime.Composable
import app.masary.core.models.auth.StudentSession

@Composable
internal fun AuthenticatedContentHost(
    state: LoginUiState,
    onLogin: (String, String) -> Unit,
    onLogout: () -> Unit,
    authenticatedContent: (@Composable (StudentSession, () -> Unit) -> Unit)?,
) {
    if (state is LoginUiState.Success && authenticatedContent != null) {
        authenticatedContent(state.session, onLogout)
    } else {
        AuthScreen(state, onLogin, onLogout)
    }
}
