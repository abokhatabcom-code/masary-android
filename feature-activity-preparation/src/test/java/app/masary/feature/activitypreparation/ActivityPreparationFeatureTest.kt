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
        val request = ActivityPreparationRequest.fromGuide(12, 4, "learn", 9)
        val viewModel = ActivityPreparationViewModel(
            repository = repository,
            pendingStore = pendingStore,
            request = request,
            keyFactory = { "12345678-1234-1234-1234-123456789012" },
        )

        advanceUntilIdle()
        viewModel.start()
        viewModel.start()
        advanceUntilIdle()

        assertEquals(1, repository.startCalls)
        assertEquals("12345678-1234-1234-1234-123456789012", repository.lastKey)
        assertTrue(viewModel.state.value is ActivityPreparationUiState.Started)
    }

    private class MemoryPendingStore : ActivityPreparationPendingStore {
        private var value: PendingActivityStart? = null
        override suspend fun read(): PendingActivityStart? = value
        override suspend fun write(value: PendingActivityStart) { this.value = value }
        override suspend fun clear() { value = null }
    }

    private class FakeRepository : ActivityPreparationRepository {
        var startCalls = 0
        var lastKey = ""

        override suspend fun preview(request: ActivityPreparationRequest): Result<ActivityPreparationPreview> =
            Result.success(preview())

        override suspend fun start(
            request: ActivityPreparationRequest,
            idempotencyKey: String,
        ): Result<ActivityStartResult> {
            startCalls += 1
            lastKey = idempotencyKey
            return Result.success(startResult())
        }

        override suspend fun startStatus(idempotencyKey: String): Result<ActivityStartResult> =
            Result.failure(ActivityPreparationStartNotFoundException())

        private fun preview(): ActivityPreparationPreview = ActivityPreparationPreview(
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

        private fun startResult(): ActivityStartResult = ActivityStartResult(
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
