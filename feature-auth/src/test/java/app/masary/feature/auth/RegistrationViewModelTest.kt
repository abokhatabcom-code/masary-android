package app.masary.feature.auth

import androidx.lifecycle.SavedStateHandle
import app.masary.core.datastore.SessionManager
import app.masary.core.models.auth.*
import app.masary.feature.auth.domain.*
import app.masary.feature.auth.ui.RegistrationViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RegistrationViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun teardown() = Dispatchers.resetMain()

    @Test fun `draft and step survive recreation including sensitive input`() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        val repository = FakeRegistrationRepository()
        val first = viewModel(handle, repository)
        advanceUntilIdle()
        val draft = RegistrationDraft("طالب", "student_1", "777", "a@example.com", "secret", "secret", "male", "calm", RegistrationCity(1, "عدن", false), grade = AcademicOption(7, "السابع"))
        first.updateDraft(draft)
        first.nextAccount()
        val recreated = viewModel(handle, repository)
        advanceUntilIdle()
        assertEquals(2, recreated.state.value.step)
        assertEquals("secret", recreated.state.value.draft.password)
        assertEquals("a@example.com", recreated.state.value.draft.email)
    }

    @Test fun `retryable failure retains password and idempotency key`() = runTest(dispatcher) {
        val repository = FakeRegistrationRepository(Result.failure(RegistrationException(RegistrationFailureKind.RETRYABLE)))
        val handle = SavedStateHandle()
        val vm = viewModel(handle, repository)
        advanceUntilIdle()
        vm.updateDraft(validDraft())
        vm.submit(true); advanceUntilIdle()
        val firstKey = repository.keys.single()
        vm.submit(true); advanceUntilIdle()
        assertEquals(listOf(firstKey, firstKey), repository.keys)
        assertEquals("secret", vm.state.value.draft.password)
    }

    @Test fun `session save failure never navigates or clears sensitive draft`() = runTest(dispatcher) {
        val authenticated = AuthenticatedStudent(Student("1", "student_1", "طالب"), AuthTokens("a", "r", 900))
        var navigated = false
        val vm = RegistrationViewModel(SavedStateHandle(), FakeRegistrationRepository(Result.success(authenticated)), FailingSessionManager, "test", { navigated = true }, {})
        advanceUntilIdle(); vm.updateDraft(validDraft()); vm.submit(true); advanceUntilIdle()
        assertFalse(navigated)
        assertEquals("secret", vm.state.value.draft.password)
        assertNotNull(vm.state.value.error)
    }

    private fun viewModel(handle: SavedStateHandle, repository: FakeRegistrationRepository) = RegistrationViewModel(handle, repository, NoopSessionManager, "test", {}, {})
    private fun validDraft() = RegistrationDraft("طالب", "student_1", password = "secret", passwordConfirmation = "secret", gender = "male", city = RegistrationCity(1, "عدن", false), grade = AcademicOption(7, "السابع"))
}

private class FakeRegistrationRepository(private val registration: Result<AuthenticatedStudent> = Result.failure(RegistrationException(RegistrationFailureKind.RETRYABLE))) : RegistrationRepository {
    val keys = mutableListOf<String>()
    override suspend fun cities() = Result.success(listOf(RegistrationCity(1, "عدن", false)))
    override suspend fun grades() = Result.success(listOf(AcademicOption(7, "السابع")))
    override suspend fun schools(cityId: Long) = Result.success(emptyList<AcademicOption>())
    override suspend fun register(draft: RegistrationDraft, deviceName: String, idempotencyKey: String): Result<AuthenticatedStudent> { keys += idempotencyKey; return registration }
}
private object NoopSessionManager : SessionManager { override val session = MutableStateFlow<StudentSession?>(null); override suspend fun save(authenticatedStudent: AuthenticatedStudent) = Unit; override suspend fun clear() = Unit }
private object FailingSessionManager : SessionManager { override val session = MutableStateFlow<StudentSession?>(null); override suspend fun save(authenticatedStudent: AuthenticatedStudent): Unit = error("storage detail"); override suspend fun clear() = Unit }
