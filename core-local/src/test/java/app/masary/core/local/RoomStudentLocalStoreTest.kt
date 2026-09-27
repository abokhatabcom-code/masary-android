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
