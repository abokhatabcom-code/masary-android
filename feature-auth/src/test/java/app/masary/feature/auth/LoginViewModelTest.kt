package app.masary.feature.auth

import app.masary.core.datastore.SessionManager
import app.masary.core.datastore.StudentSession
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

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `valid login saves session and succeeds`() = runTest(dispatcher) {
        val expected = StudentSession("42", "سارة", "token")
        val sessions = FakeSessionManager()
        val viewModel = LoginViewModel(AuthRepository { _, password ->
            password.fill('\u0000')
            Result.success(expected)
        }, sessions)

        viewModel.login("42", "valid")
        assertEquals(LoginUiState.Loading, viewModel.state.value)
        advanceUntilIdle()

        assertEquals(LoginUiState.Success(expected), viewModel.state.value)
        assertEquals(expected, sessions.session.first())
    }

    @Test fun `failed login exposes error and does not create session`() = runTest(dispatcher) {
        val sessions = FakeSessionManager()
        val viewModel = LoginViewModel(AuthRepository { _, _ -> Result.failure(Exception("مرفوض")) }, sessions)

        viewModel.login("42", "wrong")
        advanceUntilIdle()

        assertEquals(LoginUiState.Error("مرفوض"), viewModel.state.value)
        assertEquals(null, sessions.session.first())
    }

    @Test fun `validation rejects blank fields without repository call`() = runTest(dispatcher) {
        var calls = 0
        val viewModel = LoginViewModel(AuthRepository { _, _ -> calls++; Result.failure(Exception()) }, FakeSessionManager())
        advanceUntilIdle()

        viewModel.login(" ", "value")
        assertEquals(LoginUiState.Error("أدخل رقم الطالب"), viewModel.state.value)
        viewModel.login("42", "")
        assertEquals(LoginUiState.Error("أدخل كلمة المرور"), viewModel.state.value)
        assertEquals(0, calls)
    }

    @Test fun `second click is ignored while loading`() = runTest(dispatcher) {
        var calls = 0
        val session = StudentSession("42", "سارة", "token")
        val viewModel = LoginViewModel(AuthRepository { _, _ -> calls++; Result.success(session) }, FakeSessionManager())

        viewModel.login("42", "valid")
        viewModel.login("42", "valid")
        advanceUntilIdle()

        assertEquals(1, calls)
    }

    @Test fun `existing session is restored and logout clears it`() = runTest(dispatcher) {
        val existing = StudentSession("42", "سارة", "token")
        val sessions = FakeSessionManager(existing)
        val viewModel = LoginViewModel(AuthRepository { _, _ -> error("unused") }, sessions)
        advanceUntilIdle()
        assertEquals(LoginUiState.Success(existing), viewModel.state.value)

        viewModel.logout()
        advanceUntilIdle()
        assertEquals(LoginUiState.Idle, viewModel.state.value)
        assertEquals(null, sessions.session.first())
    }
}

private class FakeSessionManager(initial: StudentSession? = null) : SessionManager {
    private val mutableSession = MutableStateFlow(initial)
    override val session = mutableSession
    override suspend fun save(session: StudentSession) { mutableSession.value = session }
    override suspend fun clear() { mutableSession.value = null }
}
