package app.masary.feature.auth.ui

import app.masary.core.models.auth.StudentSession
import org.junit.Assert.assertEquals
import org.junit.Test

class StartupDestinationTest {
    @Test fun `restoring session stays on startup destination`() {
        assertEquals(StartupDestination.Restoring, LoginUiState.Restoring.startupDestination())
    }

    @Test fun `unauthenticated states share login destination`() {
        assertEquals(StartupDestination.Login, LoginUiState.Idle.startupDestination())
        assertEquals(StartupDestination.Login, LoginUiState.Loading.startupDestination())
        assertEquals(StartupDestination.Login, LoginUiState.Error("error").startupDestination())
    }

    @Test fun `valid session opens authenticated destination`() {
        val session = StudentSession("42", "student", "سارة")

        assertEquals(
            StartupDestination.Authenticated,
            LoginUiState.Success(session).startupDestination(),
        )
    }
}
