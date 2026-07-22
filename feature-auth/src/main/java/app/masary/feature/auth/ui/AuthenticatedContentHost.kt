package app.masary.feature.auth.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.masary.core.models.auth.StudentSession

internal enum class StartupDestination(val route: String) {
    Restoring("restoring"),
    Login("login"),
    Authenticated("authenticated"),
}

internal fun LoginUiState.startupDestination(): StartupDestination = when (this) {
    LoginUiState.Restoring -> StartupDestination.Restoring
    is LoginUiState.Success -> StartupDestination.Authenticated
    LoginUiState.Idle,
    LoginUiState.Loading,
    is LoginUiState.Error,
    -> StartupDestination.Login
}

@Composable
internal fun AuthenticatedContentHost(
    state: LoginUiState,
    onLogin: (String, String) -> Unit,
    onLogout: () -> Unit,
    authenticatedContent: (@Composable (StudentSession, () -> Unit) -> Unit)?,
) {
    val navController = rememberNavController()
    val destination = if (state is LoginUiState.Success && authenticatedContent == null) {
        StartupDestination.Login
    } else {
        state.startupDestination()
    }

    LaunchedEffect(destination) {
        if (navController.currentDestination?.route != destination.route) {
            navController.navigate(destination.route) {
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = StartupDestination.Restoring.route,
    ) {
        composable(StartupDestination.Restoring.route) {
            AuthScreen(LoginUiState.Restoring, onLogin, onLogout)
        }
        composable(StartupDestination.Login.route) {
            AuthScreen(state, onLogin, onLogout)
        }
        composable(StartupDestination.Authenticated.route) {
            val success = state as? LoginUiState.Success
            if (success != null && authenticatedContent != null) {
                authenticatedContent(success.session, onLogout)
            }
        }
    }
}
