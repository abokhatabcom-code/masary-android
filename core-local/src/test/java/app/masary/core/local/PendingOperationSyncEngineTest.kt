package app.masary.core.local

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PendingOperationSyncEngineTest {
    @Test
    fun confirmedOperationKeepsStableIdAndCommitsServerDelta() = runTest {
        val operation = operation("op-1")
        val queue = FakeQueue(listOf(operation))
        val seenIds = mutableListOf<String>()
        val delta = ConfirmedStudentDelta(
            studentId = "student-1",
            profile = ConfirmedProfileDelta(globalXp = 420),
            serverVersion = "v2",
            confirmedAtEpochMillis = 2_000L,
        )
        val engine = PendingOperationSyncEngine(
            queue = queue,
            processor = PendingOperationProcessor {
                seenIds += it.operationId
                PendingOperationSyncOutcome.Confirmed(delta)
            },
            now = monotonicClock(),
        )

        val summary = engine.syncReady("student-1")

        assertEquals(listOf("op-1"), seenIds)
        assertEquals(1, summary.confirmed)
        assertEquals(0, summary.retryScheduled)
        assertEquals("op-1", queue.confirmed.single().first)
        assertEquals(delta, queue.confirmed.single().second)
    }

    @Test
    fun retryUsesSameOperationIdOnNextAttempt() = runTest {
        val operation = operation("stable-id")
        val queue = FakeQueue(listOf(operation))
        val seenIds = mutableListOf<String>()
        var attempt = 0
        val engine = PendingOperationSyncEngine(
            queue = queue,
            processor = PendingOperationProcessor {
                seenIds += it.operationId
                attempt += 1
                if (attempt == 1) {
                    PendingOperationSyncOutcome.Retry("network")
                } else {
                    PendingOperationSyncOutcome.Confirmed()
                }
            },
            now = monotonicClock(),
        )

        val first = engine.syncReady("student-1")
        val second = engine.syncReady("student-1")

        assertEquals(listOf("stable-id", "stable-id"), seenIds)
        assertEquals(1, first.retryScheduled)
        assertEquals(1, second.confirmed)
        assertEquals(listOf("stable-id"), queue.failed.map { it.first })
        assertEquals(listOf("stable-id"), queue.confirmed.map { it.first })
    }

    @Test
    fun staleSyncingRecoveryRunsBeforeReadyBatchIsRead() = runTest {
        val queue = FakeQueue(
            operations = listOf(operation("op-1")),
            recoveredInterrupted = 2,
        )
        val engine = PendingOperationSyncEngine(
            queue = queue,
            processor = PendingOperationProcessor { PendingOperationSyncOutcome.Confirmed() },
            now = monotonicClock(),
        )

        val summary = engine.syncReady("student-1")

        assertEquals(2, summary.recoveredInterrupted)
        assertTrue(queue.events.indexOf("recover") < queue.events.indexOf("ready"))
    }

    @Test
    fun processorFailureSchedulesDurableRetry() = runTest {
        val queue = FakeQueue(listOf(operation("op-1")))
        val engine = PendingOperationSyncEngine(
            queue = queue,
            processor = PendingOperationProcessor { error("temporary upstream failure") },
            now = monotonicClock(),
        )

        val summary = engine.syncReady("student-1")

        assertEquals(1, summary.retryScheduled)
        assertEquals("op-1", queue.failed.single().first)
        assertEquals("temporary upstream failure", queue.failed.single().second)
        assertTrue(queue.confirmed.isEmpty())
    }

    @Test
    fun cancellationLeavesSyncingRowForLaterRecovery() = runTest {
        val queue = FakeQueue(listOf(operation("op-1")))
        val engine = PendingOperationSyncEngine(
            queue = queue,
            processor = PendingOperationProcessor { throw CancellationException("stop") },
            now = monotonicClock(),
        )

        try {
            engine.syncReady("student-1")
            fail("CancellationException expected")
        } catch (_: CancellationException) {
            // Expected.
        }

        assertTrue(queue.events.any { it == "syncing:op-1" })
        assertFalse(queue.events.any { it.startsWith("failed:") })
        assertTrue(queue.confirmed.isEmpty())
    }

    private fun operation(id: String) = PendingOperationEntity(
        operationId = id,
        studentId = "student-1",
        type = "question_answer",
        payloadJson = "{\"answer\":1}",
        createdAtEpochMillis = 100L,
        updatedAtEpochMillis = 100L,
    )

    private fun monotonicClock(): () -> Long {
        var value = 1_000L
        return { value++ }
    }

    private class FakeQueue(
        private val operations: List<PendingOperationEntity>,
        private val recoveredInterrupted: Int = 0,
    ) : PendingOperationQueue {
        val events = mutableListOf<String>()
        val failed = mutableListOf<Pair<String, String?>>()
        val confirmed = mutableListOf<Pair<String, ConfirmedStudentDelta?>>()

        override suspend fun recoverInterruptedOperations(
            studentId: String,
            nowEpochMillis: Long,
        ): Int {
            events += "recover"
            return recoveredInterrupted
        }

        override suspend fun readyOperations(
            studentId: String,
            nowEpochMillis: Long,
            limit: Int,
        ): List<PendingOperationEntity> {
            events += "ready"
            return operations.take(limit)
        }

        override suspend fun markOperationSyncing(
            operationId: String,
            nowEpochMillis: Long,
        ) {
            events += "syncing:$operationId"
        }

        override suspend fun markOperationFailed(
            operationId: String,
            error: String?,
            nowEpochMillis: Long,
        ) {
            events += "failed:$operationId"
            failed += operationId to error
        }

        override suspend fun confirmOperation(
            operationId: String,
            delta: ConfirmedStudentDelta?,
            nowEpochMillis: Long,
        ) {
            events += "confirmed:$operationId"
            confirmed += operationId to delta
        }
    }
}
