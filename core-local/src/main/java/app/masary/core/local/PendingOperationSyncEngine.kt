package app.masary.core.local

import kotlinx.coroutines.CancellationException

sealed interface PendingOperationSyncOutcome {
    data class Confirmed(
        val delta: ConfirmedStudentDelta? = null,
    ) : PendingOperationSyncOutcome

    data class Retry(
        val reason: String? = null,
    ) : PendingOperationSyncOutcome
}

fun interface PendingOperationProcessor {
    suspend fun process(operation: PendingOperationEntity): PendingOperationSyncOutcome
}

interface PendingOperationQueue {
    suspend fun recoverInterruptedOperations(
        studentId: String,
        nowEpochMillis: Long,
    ): Int

    suspend fun readyOperations(
        studentId: String,
        nowEpochMillis: Long,
        limit: Int,
    ): List<PendingOperationEntity>

    suspend fun markOperationSyncing(
        operationId: String,
        nowEpochMillis: Long,
    )

    suspend fun markOperationFailed(
        operationId: String,
        error: String?,
        nowEpochMillis: Long,
    )

    suspend fun confirmOperation(
        operationId: String,
        delta: ConfirmedStudentDelta?,
        nowEpochMillis: Long,
    )
}

class StudentLocalPendingOperationQueue(
    private val localStore: StudentLocalStore,
) : PendingOperationQueue {
    override suspend fun recoverInterruptedOperations(
        studentId: String,
        nowEpochMillis: Long,
    ): Int = localStore.recoverInterruptedOperations(
        studentId = studentId,
        nowEpochMillis = nowEpochMillis,
    )

    override suspend fun readyOperations(
        studentId: String,
        nowEpochMillis: Long,
        limit: Int,
    ): List<PendingOperationEntity> = localStore.readyOperations(
        studentId = studentId,
        nowEpochMillis = nowEpochMillis,
        limit = limit,
    )

    override suspend fun markOperationSyncing(
        operationId: String,
        nowEpochMillis: Long,
    ) {
        localStore.markOperationSyncing(operationId, nowEpochMillis)
    }

    override suspend fun markOperationFailed(
        operationId: String,
        error: String?,
        nowEpochMillis: Long,
    ) {
        localStore.markOperationFailed(operationId, error, nowEpochMillis)
    }

    override suspend fun confirmOperation(
        operationId: String,
        delta: ConfirmedStudentDelta?,
        nowEpochMillis: Long,
    ) {
        localStore.confirmOperation(operationId, delta, nowEpochMillis)
    }
}

data class PendingOperationSyncSummary(
    val recoveredInterrupted: Int,
    val loaded: Int,
    val confirmed: Int,
    val retryScheduled: Int,
)

class PendingOperationSyncEngine(
    private val queue: PendingOperationQueue,
    private val processor: PendingOperationProcessor,
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun syncReady(
        studentId: String,
        limit: Int = 50,
    ): PendingOperationSyncSummary {
        require(studentId.isNotBlank()) { "studentId is required" }
        val boundedLimit = limit.coerceIn(1, 100)
        val batchStartedAt = now()
        val recovered = queue.recoverInterruptedOperations(studentId, batchStartedAt)
        val ready = queue.readyOperations(studentId, batchStartedAt, boundedLimit)

        var confirmed = 0
        var retryScheduled = 0

        ready.forEach { operation ->
            check(operation.studentId == studentId) {
                "pending operation owner does not match requested student"
            }
            queue.markOperationSyncing(operation.operationId, now())
            try {
                when (val outcome = processor.process(operation)) {
                    is PendingOperationSyncOutcome.Confirmed -> {
                        queue.confirmOperation(
                            operationId = operation.operationId,
                            delta = outcome.delta,
                            nowEpochMillis = now(),
                        )
                        confirmed += 1
                    }

                    is PendingOperationSyncOutcome.Retry -> {
                        queue.markOperationFailed(
                            operationId = operation.operationId,
                            error = outcome.reason,
                            nowEpochMillis = now(),
                        )
                        retryScheduled += 1
                    }
                }
            } catch (cancelled: CancellationException) {
                // Keep the operation in syncing. A later run recovers stale syncing rows
                // with the exact same operation_id, preserving server idempotency.
                throw cancelled
            } catch (error: Throwable) {
                queue.markOperationFailed(
                    operationId = operation.operationId,
                    error = error.message ?: error::class.java.simpleName,
                    nowEpochMillis = now(),
                )
                retryScheduled += 1
            }
        }

        return PendingOperationSyncSummary(
            recoveredInterrupted = recovered,
            loaded = ready.size,
            confirmed = confirmed,
            retryScheduled = retryScheduled,
        )
    }
}
