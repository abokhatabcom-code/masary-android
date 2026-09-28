package app.masary.feature.questionsession.ui

import app.masary.feature.questionsession.domain.QuestionAnswerInput
import app.masary.feature.questionsession.domain.QuestionAnswerSyncSummary
import app.masary.feature.questionsession.domain.QuestionItem
import app.masary.feature.questionsession.domain.QuestionOption
import app.masary.feature.questionsession.domain.QuestionPayload
import app.masary.feature.questionsession.domain.QuestionSessionInfo
import app.masary.feature.questionsession.domain.QuestionSessionPackage
import app.masary.feature.questionsession.domain.QuestionSessionProgress
import app.masary.feature.questionsession.domain.QuestionSessionRepository
import app.masary.feature.questionsession.domain.QuestionSessionResult
import app.masary.feature.questionsession.domain.QuestionSessionScore
import app.masary.feature.questionsession.domain.QuestionType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QuestionSessionViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `shows local package immediately while network refresh is pending`() = runTest(dispatcher) {
        val cached = packageAt(index = 1, version = "cached")
        val remote = CompletableDeferred<Result<QuestionSessionPackage>>()
        val repository = FakeRepository(snapshot = cached, remote = remote)
        val model = QuestionSessionViewModel("activity-session-001", repository)

        runCurrent()

        val state = model.state.value as QuestionSessionUiState.Content
        assertEquals("cached", state.data.version)
        assertTrue(state.isRefreshing)
        assertEquals(1, state.data.progress.currentIndex)
        assertEquals(1, repository.syncCalls)
    }

    @Test
    fun `submitting answer advances locally without waiting for server result`() = runTest(dispatcher) {
        val initial = packageAt(index = 0, version = "cached")
        val remote = CompletableDeferred<Result<QuestionSessionPackage>>()
        val repository = FakeRepository(snapshot = initial, remote = remote)
        val model = QuestionSessionViewModel("activity-session-001", repository)

        runCurrent()
        model.submit(QuestionAnswerInput.Choice("option-a"))
        runCurrent()

        val state = model.state.value as QuestionSessionUiState.Content
        assertEquals(1, state.data.progress.currentIndex)
        assertEquals("q1", repository.savedQuestionId)
        assertEquals(1, repository.savedNextIndex)
        assertEquals(false, repository.savedCompleted)
        assertEquals(2, repository.syncCalls)
    }

    @Test
    fun `last local answer stays completed local when server finish is unavailable`() = runTest(dispatcher) {
        val initial = packageAt(index = 1, version = "cached")
        val remote = CompletableDeferred<Result<QuestionSessionPackage>>()
        val repository = FakeRepository(snapshot = initial, remote = remote)
        val model = QuestionSessionViewModel("activity-session-001", repository)

        runCurrent()
        model.submit(QuestionAnswerInput.Choice("option-a"))
        runCurrent()

        assertTrue(model.state.value is QuestionSessionUiState.CompletedLocal)
        assertEquals(2, repository.savedNextIndex)
        assertTrue(repository.savedCompleted)
    }

    @Test
    fun `last answer shows confirmed result only after server finish succeeds`() = runTest(dispatcher) {
        val initial = packageAt(index = 1, version = "cached")
        val remote = CompletableDeferred<Result<QuestionSessionPackage>>()
        val expected = QuestionSessionResult(
            sessionId = "activity-session-001",
            completedAt = "2026-09-28 12:00:00",
            replayed = false,
            score = QuestionSessionScore(
                correctAnswers = 2,
                incorrectAnswers = 0,
                totalQuestions = 2,
                scorePercent = 100,
            ),
            confirmedDeltaAvailable = false,
            confirmedDeltaReason = "لا توجد مكافآت مؤكدة.",
        )
        val repository = FakeRepository(
            snapshot = initial,
            remote = remote,
            finishResult = Result.success(expected),
        )
        val model = QuestionSessionViewModel("activity-session-001", repository)

        runCurrent()
        model.submit(QuestionAnswerInput.Choice("option-a"))
        runCurrent()

        val state = model.state.value as QuestionSessionUiState.Result
        assertEquals(100, state.data.score.scorePercent)
        assertEquals(1, repository.finishCalls)
    }

    private fun packageAt(index: Int, version: String): QuestionSessionPackage {
        val questions = listOf(
            question("q1"),
            question("q2"),
        )
        return QuestionSessionPackage(
            version = version,
            generatedAt = "2026-09-28T09:00:00Z",
            session = QuestionSessionInfo(
                id = "activity-session-001",
                status = "created",
                expiresAt = "2099-01-01 00:00:00",
                subjectVersionId = 12,
                unitId = null,
                lessonId = null,
                activityType = "choose_test",
                activityMode = "practice",
            ),
            progress = QuestionSessionProgress(index, questions.size),
            questions = questions,
        )
    }

    private fun question(id: String) = QuestionItem(
        id = id,
        type = QuestionType.Choose,
        prompt = "سؤال",
        payload = QuestionPayload.Options(
            listOf(
                QuestionOption("option-a", "أ"),
                QuestionOption("option-b", "ب"),
            ),
        ),
    )

    private class FakeRepository(
        snapshot: QuestionSessionPackage,
        private val remote: CompletableDeferred<Result<QuestionSessionPackage>>,
        private val finishResult: Result<QuestionSessionResult> =
            Result.failure(IllegalStateException("offline")),
    ) : QuestionSessionRepository {
        private var local = snapshot
        var savedQuestionId: String? = null
        var savedNextIndex: Int = -1
        var savedCompleted: Boolean = false
        var syncCalls: Int = 0
        var finishCalls: Int = 0

        override suspend fun loadPackage(sessionId: String): Result<QuestionSessionPackage> =
            remote.await()

        override suspend fun loadSnapshot(sessionId: String): QuestionSessionPackage = local

        override suspend fun saveLocalAnswer(
            sessionId: String,
            questionId: String,
            answer: QuestionAnswerInput,
            nextQuestionIndex: Int,
            completed: Boolean,
        ): Result<Unit> {
            savedQuestionId = questionId
            savedNextIndex = nextQuestionIndex
            savedCompleted = completed
            local = local.copy(
                progress = local.progress.copy(currentIndex = nextQuestionIndex),
            )
            return Result.success(Unit)
        }

        override suspend fun syncPendingAnswers(): Result<QuestionAnswerSyncSummary> {
            syncCalls += 1
            return Result.success(
                QuestionAnswerSyncSummary(
                    attempted = 0,
                    confirmed = 0,
                    retryScheduled = 0,
                ),
            )
        }

        override suspend fun loadResult(sessionId: String): QuestionSessionResult? = null

        override suspend fun finishSession(sessionId: String): Result<QuestionSessionResult> {
            finishCalls += 1
            return finishResult
        }
    }
}
