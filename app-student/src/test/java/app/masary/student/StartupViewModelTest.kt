package app.masary.student

import app.masary.core.datastore.OnboardingStore
import app.masary.core.datastore.SessionManager
import app.masary.core.models.auth.AuthenticatedStudent
import app.masary.core.models.auth.AuthTokens
import app.masary.core.models.auth.StudentSession
import app.masary.feature.auth.domain.AuthFailureException
import app.masary.feature.auth.domain.AuthFailureKind
import app.masary.feature.auth.domain.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StartupViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val session = StudentSession("7", "student", "سارة")
    private val tokens = AuthTokens("access", "refresh", 3600, Long.MAX_VALUE)

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `first launch requires onboarding`() = runTest(dispatcher) {
        val viewModel = StartupViewModel(FakeOnboardingStore(false), FakeSessionManager(), FakeAuthRepository())
        advanceUntilIdle()
        assertEquals(StartupState.NeedsOnboarding, viewModel.state.value)
    }

    @Test fun `completing or skipping onboarding persists it and opens login`() = runTest(dispatcher) {
        val onboarding = FakeOnboardingStore(false)
        val viewModel = StartupViewModel(onboarding, FakeSessionManager(), FakeAuthRepository())
        advanceUntilIdle()
        viewModel.completeOnboarding()
        advanceUntilIdle()
        assertTrue(onboarding.completed.value)
        assertEquals(StartupState.NeedsAuthentication, viewModel.state.value)
    }

    @Test fun `completed onboarding is not shown again`() = runTest(dispatcher) {
        val viewModel = StartupViewModel(FakeOnboardingStore(true), FakeSessionManager(), FakeAuthRepository())
        advanceUntilIdle()
        assertEquals(StartupState.NeedsAuthentication, viewModel.state.value)
    }

    @Test fun `valid session opens home`() = runTest(dispatcher) {
        val viewModel = StartupViewModel(
            FakeOnboardingStore(true),
            FakeSessionManager(session, tokens),
            FakeAuthRepository(),
        )
        advanceUntilIdle()
        assertEquals(StartupState.Authenticated(session), viewModel.state.value)
    }

    @Test fun `expired renewable session saves refreshed tokens`() = runTest(dispatcher) {
        val expired = AuthTokens("expired", "refresh", 1, 1)
        val refreshed = AuthTokens("new", "new-refresh", 900, Long.MAX_VALUE)
        val sessions = FakeSessionManager(session, expired)
        val repository = FakeAuthRepository(validation = Result.success(refreshed))
        val viewModel = StartupViewModel(FakeOnboardingStore(true), sessions, repository)
        advanceUntilIdle()
        assertEquals(refreshed, sessions.tokens)
        assertEquals(StartupState.Authenticated(session), viewModel.state.value)
    }

    @Test fun `terminal refresh rejection clears sensitive session and opens login`() = runTest(dispatcher) {
        val sessions = FakeSessionManager(session, tokens)
        val rejection = AuthFailureException(AuthFailureKind.SESSION_REJECTED, "rejected")
        val viewModel = StartupViewModel(
            FakeOnboardingStore(true),
            sessions,
            FakeAuthRepository(Result.failure(rejection)),
        )
        advanceUntilIdle()
        assertTrue(sessions.cleared)
        assertEquals(null, sessions.tokens)
        assertEquals(StartupState.NeedsAuthentication, viewModel.state.value)
    }

    @Test fun `temporary startup failure is recoverable and retry succeeds`() = runTest(dispatcher) {
        val onboarding = FakeOnboardingStore(true, failReads = 1)
        val viewModel = StartupViewModel(onboarding, FakeSessionManager(), FakeAuthRepository())
        advanceUntilIdle()
        assertEquals(StartupState.RecoverableError, viewModel.state.value)
        viewModel.retry()
        advanceUntilIdle()
        assertEquals(StartupState.NeedsAuthentication, viewModel.state.value)
    }

    @Test fun `logout clears session even when remote logout fails`() = runTest(dispatcher) {
        val sessions = FakeSessionManager(session, tokens)
        val repository = FakeAuthRepository(logoutResult = Result.failure(Exception("offline")))
        val viewModel = StartupViewModel(FakeOnboardingStore(true), sessions, repository)
        advanceUntilIdle()
        viewModel.logout()
        advanceUntilIdle()
        assertTrue(sessions.cleared)
        assertEquals(StartupState.NeedsAuthentication, viewModel.state.value)
    }
}

private class FakeOnboardingStore(
    initial: Boolean,
    private var failReads: Int = 0,
) : OnboardingStore {
    val completed = MutableStateFlow(initial)
    override val isCompleted: Flow<Boolean>
        get() = if (failReads-- > 0) flow { throw Exception("storage unavailable") } else completed
    override suspend fun markCompleted() { completed.value = true }
    override suspend fun reset() { completed.value = false }
}

private class FakeSessionManager(
    initialSession: StudentSession? = null,
    var tokens: AuthTokens? = null,
) : SessionManager {
    private val currentSession = MutableStateFlow(initialSession)
    override val session: Flow<StudentSession?> = currentSession
    var cleared = false
    override suspend fun save(authenticatedStudent: AuthenticatedStudent) = Unit
    override suspend fun readTokens(): AuthTokens? = tokens
    override suspend fun updateTokens(tokens: AuthTokens) { this.tokens = tokens }
    override suspend fun clear() {
        cleared = true
        tokens = null
        currentSession.value = null
    }
}

private class FakeAuthRepository(
    private val validation: Result<AuthTokens>? = null,
    private val logoutResult: Result<Unit> = Result.success(Unit),
) : AuthRepository {
    override suspend fun login(
        username: String,
        password: CharArray,
        deviceName: String,
    ): Result<AuthenticatedStudent> = error("unused")

    override suspend fun validateSession(tokens: AuthTokens): Result<AuthTokens> =
        validation ?: Result.success(tokens)

    override suspend fun logout(tokens: AuthTokens): Result<Unit> = logoutResult
}
