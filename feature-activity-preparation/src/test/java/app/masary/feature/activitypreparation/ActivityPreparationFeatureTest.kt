package app.masary.feature.activitypreparation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ActivityPreparationFeatureTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `request fingerprint is stable and scoped to the request`() {
        val first = ActivityPreparationRequest.fromGuide(12, 4, "review_mistakes", 91)
        val same = ActivityPreparationRequest.fromGuide(12, 4, "review_mistakes", 91)
        val different = same.copy(unitId = 5)

        assertEquals(first.fingerprint, same.fingerprint)
        assertNotEquals(first.fingerprint, different.fingerprint)
    }

    @Test
    fun `invalid identifiers are rejected before network access`() {
        assertThrows(IllegalArgumentException::class.java) {
            ActivityPreparationRequest(
                subjectVersionId = 0,
                activityType = "guide_step",
                activityMode = "learn",
                source = "guide",
            )
        }
    }

    @Test
    fun `double start creates only one server request with the original key`() = runTest {
        val repository = FakeRepository()
        val pendingStore = MemoryPendingStore()
        val request = request()
        val viewModel = ActivityPreparationViewModel(
            repository = repository,
            pendingStore = pendingStore,
            request = request,
            keyFactory = { TEST_KEY },
        )

        advanceUntilIdle()
        viewModel.start()
        viewModel.start()
        advanceUntilIdle()

        assertEquals(1, repository.startKeys.size)
        assertEquals(TEST_KEY, repository.startKeys.single())
        assertTrue(viewModel.state.value is ActivityPreparationUiState.Started)
    }

    @Test
    fun `unknown network result is resolved with the same idempotency key`() = runTest {
        val repository = FakeRepository(
            startResults = ArrayDeque(
                listOf(
                    Result.failure(ActivityPreparationNetworkException()),
                    Result.success(startResult()),
                ),
            ),
            statusResults = ArrayDeque(
                listOf(Result.failure(ActivityPreparationStartNotFoundException())),
            ),
        )
        val pendingStore = MemoryPendingStore()
        val viewModel = ActivityPreparationViewModel(
            repository = repository,
            pendingStore = pendingStore,
            request = request(),
            keyFactory = { TEST_KEY },
        )

        advanceUntilIdle()
        viewModel.start()
        advanceUntilIdle()
        assertTrue(viewModel.state.value is ActivityPreparationUiState.StartUnknown)
        assertEquals(TEST_KEY, pendingStore.read()?.idempotencyKey)

        viewModel.resolveUnknownStart()
        advanceUntilIdle()

        assertEquals(listOf(TEST_KEY, TEST_KEY), repository.startKeys)
        assertEquals(listOf(TEST_KEY), repository.statusKeys)
        assertTrue(viewModel.state.value is ActivityPreparationUiState.Started)
    }

    @Test
    fun `restored pending start checks status before loading preview`() = runTest {
        val request = request()
        val pendingStore = MemoryPendingStore(
            PendingActivityStart(
                idempotencyKey = TEST_KEY,
                requestFingerprint = request.fingerprint,
                createdAtEpochMillis = 1_000L,
            ),
        )
        val repository = FakeRepository(
            statusResults = ArrayDeque(listOf(Result.success(startResult()))),
        )

        val viewModel = ActivityPreparationViewModel(
            repository = repository,
            pendingStore = pendingStore,
            request = request,
            now = { 1_500L },
        )
        advanceUntilIdle()

        assertEquals(0, repository.previewCalls)
        assertEquals(listOf(TEST_KEY), repository.statusKeys)
        assertTrue(viewModel.state.value is ActivityPreparationUiState.Started)
    }

    @Test
    fun `expired bearer session moves preparation to session expired state`() = runTest {
        val repository = FakeRepository(
            previewResult = Result.failure(ActivityPreparationSessionExpiredException()),
        )
        val viewModel = ActivityPreparationViewModel(
            repository = repository,
            pendingStore = MemoryPendingStore(),
            request = request(),
        )

        advanceUntilIdle()

        assertTrue(viewModel.state.value is ActivityPreparationUiState.SessionExpired)
    }

    private class MemoryPendingStore(
        private var value: PendingActivityStart? = null,
    ) : ActivityPreparationPendingStore {
        override suspend fun read(): PendingActivityStart? = value
        override suspend fun write(value: PendingActivityStart) {
            this.value = value
        }
        override suspend fun clear() {
            value = null
        }
    }

    private class FakeRepository(
        private val previewResult: Result<ActivityPreparationPreview> = Result.success(preview()),
        private val startResults: ArrayDeque<Result<ActivityStartResult>> =
            ArrayDeque(listOf(Result.success(startResult()))),
        private val statusResults: ArrayDeque<Result<ActivityStartResult>> =
            ArrayDeque(listOf(Result.failure(ActivityPreparationStartNotFoundException()))),
    ) : ActivityPreparationRepository {
        var previewCalls = 0
        val startKeys = mutableListOf<String>()
        val statusKeys = mutableListOf<String>()

        override suspend fun preview(request: ActivityPreparationRequest): Result<ActivityPreparationPreview> {
            previewCalls += 1
            return previewResult
        }

        override suspend fun start(
            request: ActivityPreparationRequest,
            idempotencyKey: String,
        ): Result<ActivityStartResult> {
            startKeys += idempotencyKey
            return if (startResults.isEmpty()) {
                Result.success(startResult())
            } else {
                startResults.removeFirst()
            }
        }

        override suspend fun startStatus(idempotencyKey: String): Result<ActivityStartResult> {
            statusKeys += idempotencyKey
            return if (statusResults.isEmpty()) {
                Result.failure(ActivityPreparationStartNotFoundException())
            } else {
                statusResults.removeFirst()
            }
        }
    }

    private companion object {
        const val TEST_KEY = "12345678-1234-1234-1234-123456789012"

        fun request(): ActivityPreparationRequest =
            ActivityPreparationRequest.fromGuide(12, 4, "learn", 9)

        fun preview(): ActivityPreparationPreview = ActivityPreparationPreview(
            version = "v1",
            generatedAt = "now",
            activity = ActivityDescriptor(
                subjectVersionId = 12,
                subjectName = "الرياضيات",
                unitId = 4,
                unitTitle = "الوحدة الأولى",
                lessonId = null,
                lessonTitle = "",
                activityType = "guide_step",
                activityMode = "learn",
                title = "ابدأ",
                estimatedMinutes = 5,
                questionCount = null,
            ),
            eligibility = ActivityEligibility(true, "ready", "", ""),
            balances = ActivityBalances(3, 10),
            cost = ActivityCost(0, 0, 0),
            attempts = ActivityAttempts(false, null, null, null, ""),
            resume = ActivityResume(false, null, "", "", ""),
        )

        fun startResult(): ActivityStartResult = ActivityStartResult(
            sessionId = "session-1",
            status = "created",
            destination = "activity_session_pending_ui",
            replayed = false,
            debit = ActivityDebit(0, 0),
            balances = ActivityBalances(3, 10),
            expiresAt = "later",
        )
    }
}
