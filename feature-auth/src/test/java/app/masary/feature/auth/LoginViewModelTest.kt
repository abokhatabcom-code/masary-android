package app.masary.feature.auth

import app.masary.core.datastore.SessionManager
import app.masary.core.models.auth.AuthenticatedStudent
import app.masary.core.models.auth.AuthTokens
import app.masary.core.models.auth.Student
import app.masary.core.models.auth.StudentSession
import app.masary.feature.auth.domain.AuthRepository
import app.masary.feature.auth.ui.LoginUiState
import app.masary.feature.auth.ui.LoginViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val authenticated = AuthenticatedStudent(
        Student("42", "student", "سارة"),
        AuthTokens("access", "refresh", 3600),
    )

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `valid login saves session and succeeds`() = runTest(dispatcher) {
        val sessions = FakeSessionManager()
        val viewModel = viewModel(AuthRepository { _, password, device ->
            password.fill('\u0000')
            assertEquals("test-device", device)
            Result.success(authenticated)
        }, sessions)
        advanceUntilIdle()

        viewModel.login("student", "valid")
        assertEquals(LoginUiState.Loading, viewModel.state.value)
        advanceUntilIdle()

        assertEquals(LoginUiState.Success(StudentSession("42", "student", "سارة")), viewModel.state.value)
        assertEquals(StudentSession("42", "student", "سارة"), sessions.session.first())
        assertEquals(authenticated, sessions.saved)
    }

    @Test fun `failed login exposes error and does not create session`() = runTest(dispatcher) {
        val sessions = FakeSessionManager()
        val viewModel = viewModel(AuthRepository { _, _, _ -> Result.failure(Exception("مرفوض")) }, sessions)
        advanceUntilIdle()

        viewModel.login("student", "wrong")
        advanceUntilIdle()

        assertEquals(LoginUiState.Error("مرفوض"), viewModel.state.value)
        assertEquals(null, sessions.session.first())
    }

    @Test fun `validation rejects blank fields without repository call`() = runTest(dispatcher) {
        var calls = 0
        val viewModel = viewModel(AuthRepository { _, _, _ -> calls++; Result.failure(Exception()) }, FakeSessionManager())
        advanceUntilIdle()

        viewModel.login(" ", "value")
        assertEquals(LoginUiState.Error("أدخل اسم المستخدم"), viewModel.state.value)
        viewModel.login("student", "")
        assertEquals(LoginUiState.Error("أدخل كلمة المرور"), viewModel.state.value)
        assertEquals(0, calls)
    }

    @Test fun `second click is ignored while loading`() = runTest(dispatcher) {
        var calls = 0
        val viewModel = viewModel(AuthRepository { _, _, _ -> calls++; Result.success(authenticated) }, FakeSessionManager())
        advanceUntilIdle()

        viewModel.login("student", "valid")
        viewModel.login("student", "valid")
        advanceUntilIdle()

        assertEquals(1, calls)
    }

    @Test fun `existing valid session is restored and logout clears session and tokens`() = runTest(dispatcher) {
        val existing = StudentSession("42", "student", "سارة")
        val tokens = AuthTokens("access", "refresh", 3600)
        val sessions = FakeSessionManager(existing, tokens)
        val viewModel = viewModel(AuthRepository { _, _, _ -> error("unused") }, sessions)
        advanceUntilIdle()
        assertEquals(LoginUiState.Success(existing), viewModel.state.value)

        viewModel.logout()
        advanceUntilIdle()
        assertEquals(LoginUiState.Idle, viewModel.state.value)
        assertEquals(null, sessions.session.first())
        assertEquals(true, sessions.cleared)
    }

    @Test fun `expired access token is refreshed during restoration`() = runTest(dispatcher) {
        val existing = StudentSession("42", "student", "سارة")
        val expired = AuthTokens("expired", "refresh", 1, 1)
        val refreshed = AuthTokens("new-access", "new-refresh", 900, Long.MAX_VALUE)
        val sessions = FakeSessionManager(existing, expired)
        val repository = object : AuthRepository {
            override suspend fun login(
                username: String,
                password: CharArray,
                deviceName: String,
            ): Result<AuthenticatedStudent> = error("unused")

            override suspend fun refresh(refreshToken: String): Result<AuthTokens> = Result.success(refreshed)
        }

        val viewModel = viewModel(repository, sessions)
        advanceUntilIdle()

        assertEquals(LoginUiState.Success(existing), viewModel.state.value)
        assertEquals(refreshed, sessions.tokens)
    }

    private fun viewModel(repository: AuthRepository, sessions: FakeSessionManager) =
        LoginViewModel(repository, sessions, "test-device")
}

private class FakeSessionManager(
    initial: StudentSession? = null,
    initialTokens: AuthTokens? = null,
) : SessionManager {
    private val mutableSession = MutableStateFlow(initial)
    override val session = mutableSession
    var saved: AuthenticatedStudent? = null
    var tokens: AuthTokens? = initialTokens
    var cleared = false

    override suspend fun save(authenticatedStudent: AuthenticatedStudent) {
        saved = authenticatedStudent
        tokens = authenticatedStudent.tokens
        val student = authenticatedStudent.student
        mutableSession.value = StudentSession(student.id, student.username, student.displayName)
    }

    override suspend fun readTokens(): AuthTokens? = tokens

    override suspend fun updateTokens(tokens: AuthTokens) {
        this.tokens = tokens
    }

    override suspend fun clear() {
        cleared = true
        tokens = null
        mutableSession.value = null
    }
}
