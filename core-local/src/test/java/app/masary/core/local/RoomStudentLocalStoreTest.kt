package app.masary.core.local

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomStudentLocalStoreTest {
    private lateinit var context: Context
    private lateinit var database: MasaryLocalDatabase
    private lateinit var store: RoomStudentLocalStore

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(DATABASE_NAME)
        openDatabase()
    }

    @After
    fun tearDown() {
        if (::database.isInitialized && database.isOpen) {
            database.close()
        }
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun clearStudentOnlyRemovesThatStudentsLocalData() = runTest {
        store.replaceProfileState(StudentProfileStateEntity(studentId = STUDENT_A, globalXp = 120))
        store.replaceProfileState(StudentProfileStateEntity(studentId = STUDENT_B, globalXp = 880))
        store.putDocument(STUDENT_A, "home", "{\"owner\":\"a\"}")
        store.putDocument(STUDENT_B, "home", "{\"owner\":\"b\"}")
        store.enqueueOperation(operation("op-a", STUDENT_A))
        store.enqueueOperation(operation("op-b", STUDENT_B))

        store.clearStudent(STUDENT_A)

        assertNull(store.observeProfile(STUDENT_A).first())
        assertEquals(880, store.observeProfile(STUDENT_B).first()?.globalXp)
        assertNull(store.readDocument(STUDENT_A, "home"))
        assertNotNull(store.readDocument(STUDENT_B, "home"))
        assertTrue(store.readyOperations(STUDENT_A, nowEpochMillis = 10_000L, limit = 10).isEmpty())
        assertEquals(
            "op-b",
            store.readyOperations(STUDENT_B, nowEpochMillis = 10_000L, limit = 10).single().operationId,
        )
    }

    @Test
    fun deleteDocumentKindIsScopedToOneStudent() = runTest {
        store.putDocument(STUDENT_A, "home", "{\"owner\":\"a\"}")
        store.putDocument(STUDENT_B, "home", "{\"owner\":\"b\"}")

        store.deleteDocumentKind(STUDENT_A, "home")

        assertNull(store.readDocument(STUDENT_A, "home"))
        assertNotNull(store.readDocument(STUDENT_B, "home"))
    }

    @Test
    fun duplicateOperationIdCannotBeReassignedToAnotherStudent() = runTest {
        store.enqueueOperation(operation("shared-id", STUDENT_A))

        try {
            store.enqueueOperation(operation("shared-id", STUDENT_B))
            throw AssertionError("Cross-student operation id collision must be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }

        assertEquals(
            STUDENT_A,
            store.readyOperations(STUDENT_A, nowEpochMillis = 10_000L, limit = 10)
                .single()
                .studentId,
        )
        assertTrue(
            store.readyOperations(STUDENT_B, nowEpochMillis = 10_000L, limit = 10)
                .isEmpty(),
        )
    }

    @Test
    fun questionSessionIdCannotBeReassignedToAnotherStudent() = runTest {
        store.saveQuestionSession(questionSession("session-1", STUDENT_A))

        try {
            store.saveQuestionSession(questionSession("session-1", STUDENT_B))
            throw AssertionError("Cross-student session id collision must be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }

        assertEquals(
            STUDENT_A,
            database.questionSessionDao().readSessionById("session-1")?.studentId,
        )
    }

    @Test
    fun recordingAnswerCannotStealAnotherStudentsOperationId() = runTest {
        store.enqueueOperation(operation("shared-answer-op", STUDENT_A))
        store.saveQuestionSession(questionSession("session-b", STUDENT_B))
        val answer = QuestionAnswerEntity(
            studentId = STUDENT_B,
            sessionId = "session-b",
            questionId = "q1",
            answerJson = "{}",
            localSequence = 1,
            answeredAtEpochMillis = 500L,
        )

        try {
            store.recordQuestionAnswer(
                answer = answer,
                nextQuestionIndex = 1,
                sessionStatus = "in_progress",
                operation = operation("shared-answer-op", STUDENT_B),
            )
            throw AssertionError("Cross-student operation id collision must be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }

        assertTrue(store.readQuestionAnswers(STUDENT_B, "session-b").isEmpty())
        assertEquals(
            0,
            database.questionSessionDao().readSession(STUDENT_B, "session-b")?.currentQuestionIndex,
        )
        assertEquals(
            STUDENT_A,
            database.pendingOperationDao().read("shared-answer-op")?.studentId,
        )
    }

    @Test
    fun oldestCompletedLocalQuestionSessionIgnoresInProgressRows() = runTest {
        store.saveQuestionSession(
            questionSession("session-newer", STUDENT_A).copy(
                status = "completed_local",
                updatedAtEpochMillis = 300L,
            ),
        )
        store.saveQuestionSession(
            questionSession("session-in-progress", STUDENT_A).copy(
                status = "in_progress",
                updatedAtEpochMillis = 50L,
            ),
        )
        store.saveQuestionSession(
            questionSession("session-older", STUDENT_A).copy(
                status = "completed_local",
                updatedAtEpochMillis = 200L,
            ),
        )

        val completed = store.readOldestCompletedLocalQuestionSession(STUDENT_A)

        assertEquals("session-older", completed?.sessionId)
    }

    @Test
    fun questionSessionAndAnswersSurviveDatabaseReopen() = runTest {
        val session = questionSession("session-resume", STUDENT_A).copy(currentQuestionIndex = 2)
        store.saveQuestionSession(session)
        store.saveQuestionAnswer(
            QuestionAnswerEntity(
                studentId = STUDENT_A,
                sessionId = session.sessionId,
                questionId = "q1",
                answerJson = "{}",
                localSequence = 1,
                answeredAtEpochMillis = 400L,
            ),
        )

        database.close()
        openDatabase()

        val restoredSession = store.observeResumableQuestionSession(STUDENT_A).first()
        val restoredAnswers = store.readQuestionAnswers(STUDENT_A, session.sessionId)

        assertEquals("session-resume", restoredSession?.sessionId)
        assertEquals(2, restoredSession?.currentQuestionIndex)
        assertEquals("q1", restoredAnswers.single().questionId)
    }

    @Test
    fun offlineQuestionAnswerProgressAndPendingOperationSurviveDatabaseReopen() = runTest {
        val session = questionSession("session-offline", STUDENT_A)
        val operation = operation("offline-answer-op", STUDENT_A).copy(
            payloadJson = """{"session_id":"session-offline","question_id":"q1"}""",
        )
        store.saveQuestionSession(session)

        store.recordQuestionAnswer(
            answer = QuestionAnswerEntity(
                studentId = STUDENT_A,
                sessionId = session.sessionId,
                questionId = "q1",
                answerJson = """{"kind":"choice","option_id":"opaque-option"}""",
                localSequence = 1,
                answeredAtEpochMillis = 500L,
            ),
            nextQuestionIndex = 1,
            sessionStatus = "in_progress",
            operation = operation,
        )

        database.close()
        openDatabase()

        val restoredSession =
            database.questionSessionDao().readSession(STUDENT_A, session.sessionId)
        val restoredAnswers = store.readQuestionAnswers(STUDENT_A, session.sessionId)
        val restoredOperations = store.readyOperations(
            studentId = STUDENT_A,
            nowEpochMillis = 10_000L,
            limit = 10,
        )

        assertEquals(1, restoredSession?.currentQuestionIndex)
        assertEquals("q1", restoredAnswers.single().questionId)
        assertEquals("offline-answer-op", restoredOperations.single().operationId)
        assertEquals("question_answer", restoredOperations.single().type)
    }

    @Test
    fun revisingAnswerReplacesSamePendingOperationWithLatestPayload() = runTest {
        val session = questionSession("session-revision", STUDENT_A)
        store.saveQuestionSession(session)

        store.recordQuestionAnswer(
            answer = QuestionAnswerEntity(
                studentId = STUDENT_A,
                sessionId = session.sessionId,
                questionId = "q1",
                answerJson = """{"kind":"choice","option_id":"first"}""",
                localSequence = 1,
                answeredAtEpochMillis = 500L,
            ),
            nextQuestionIndex = 1,
            sessionStatus = "in_progress",
            operation = operation("stable-q1-op", STUDENT_A).copy(
                payloadJson = """{"answer":{"option_id":"first"}}""",
                updatedAtEpochMillis = 500L,
            ),
        )

        store.recordQuestionAnswer(
            answer = QuestionAnswerEntity(
                studentId = STUDENT_A,
                sessionId = session.sessionId,
                questionId = "q1",
                answerJson = """{"kind":"choice","option_id":"final"}""",
                localSequence = 2,
                answeredAtEpochMillis = 700L,
            ),
            nextQuestionIndex = 1,
            sessionStatus = "completed_local",
            operation = operation("stable-q1-op", STUDENT_A).copy(
                payloadJson = """{"answer":{"option_id":"final"}}""",
                updatedAtEpochMillis = 700L,
            ),
        )

        val answers = store.readQuestionAnswers(STUDENT_A, session.sessionId)
        val operations = store.readyOperations(
            studentId = STUDENT_A,
            nowEpochMillis = 10_000L,
            limit = 10,
        )

        assertEquals(1, answers.size)
        assertTrue(answers.single().answerJson.contains("final"))
        assertEquals(1, operations.size)
        assertEquals("stable-q1-op", operations.single().operationId)
        assertTrue(operations.single().payloadJson.contains("final"))
        assertEquals(
            "completed_local",
            database.questionSessionDao().readSession(STUDENT_A, session.sessionId)?.status,
        )
    }

    @Test
    fun syncingOperationSurvivesDatabaseReopenAndRecoversWithSameId() = runTest {
        store.enqueueOperation(operation("stable-operation", STUDENT_A))
        store.markOperationSyncing("stable-operation", nowEpochMillis = 1_000L)

        database.close()
        openDatabase()

        val recovered = store.recoverInterruptedOperations(
            studentId = STUDENT_A,
            nowEpochMillis = 10_000L,
            staleAfterMillis = 1_000L,
        )
        val ready = store.readyOperations(
            studentId = STUDENT_A,
            nowEpochMillis = 10_000L,
            limit = 10,
        )

        assertEquals(1, recovered)
        assertEquals("stable-operation", ready.single().operationId)
        assertEquals(STUDENT_A, ready.single().studentId)
    }

    @Test
    fun confirmedOperationAndServerDeltaCommitTogetherLocally() = runTest {
        store.replaceProfileState(StudentProfileStateEntity(studentId = STUDENT_A, globalXp = 100))
        store.enqueueOperation(operation("op-confirm", STUDENT_A))

        store.confirmOperation(
            operationId = "op-confirm",
            delta = ConfirmedStudentDelta(
                studentId = STUDENT_A,
                profile = ConfirmedProfileDelta(globalXp = 175, gems = 9),
                serverVersion = "server-v3",
                confirmedAtEpochMillis = 5_000L,
            ),
            nowEpochMillis = 5_000L,
        )

        val profile = store.observeProfile(STUDENT_A).first()
        val operation = database.pendingOperationDao().read("op-confirm")
        assertEquals(175, profile?.globalXp)
        assertEquals(9, profile?.gems)
        assertEquals("server-v3", profile?.serverVersion)
        assertEquals(PendingOperationState.CONFIRMED, operation?.state)
        assertEquals(0, store.observeOutstandingOperations(STUDENT_A).first())
    }

    @Test
    fun mismatchedConfirmedDeltaOwnerRollsBackOperationConfirmation() = runTest {
        store.replaceProfileState(StudentProfileStateEntity(studentId = STUDENT_A, globalXp = 100))
        store.enqueueOperation(operation("op-owner", STUDENT_A))

        try {
            store.confirmOperation(
                operationId = "op-owner",
                delta = ConfirmedStudentDelta(
                    studentId = STUDENT_B,
                    profile = ConfirmedProfileDelta(globalXp = 999),
                    confirmedAtEpochMillis = 5_000L,
                ),
                nowEpochMillis = 5_000L,
            )
            throw AssertionError("Owner mismatch must be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }

        assertEquals(100, store.observeProfile(STUDENT_A).first()?.globalXp)
        assertNull(store.observeProfile(STUDENT_B).first())
        assertEquals(
            PendingOperationState.PENDING,
            database.pendingOperationDao().read("op-owner")?.state,
        )
    }

    private fun openDatabase() {
        database = Room.databaseBuilder(
            context,
            MasaryLocalDatabase::class.java,
            DATABASE_NAME,
        ).allowMainThreadQueries().build()
        store = RoomStudentLocalStore(database)
    }

    private fun questionSession(id: String, studentId: String) = QuestionSessionEntity(
        sessionId = id,
        studentId = studentId,
        subjectVersionId = 12,
        status = "in_progress",
        packageJson = "{}",
        startedAtEpochMillis = 100L,
        updatedAtEpochMillis = 100L,
    )

    private fun operation(id: String, studentId: String) = PendingOperationEntity(
        operationId = id,
        studentId = studentId,
        type = "question_answer",
        payloadJson = "{\"answer\":1}",
        createdAtEpochMillis = 100L,
        updatedAtEpochMillis = 100L,
    )

    private companion object {
        const val DATABASE_NAME = "phase-12-6-local-test.db"
        const val STUDENT_A = "student-a"
        const val STUDENT_B = "student-b"
    }
}
