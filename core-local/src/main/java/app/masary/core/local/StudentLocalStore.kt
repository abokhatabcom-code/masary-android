package app.masary.core.local

import androidx.room.withTransaction
import app.masary.core.models.student.StudentLiveState
import app.masary.core.models.student.StudentProfileLiveState
import app.masary.core.models.student.StudentSubjectLiveState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class ConfirmedProfileDelta(
    val globalXp: Int? = null,
    val gems: Int? = null,
    val level: Int? = null,
    val levelProgressPercent: Int? = null,
    val levelNextXp: Int? = null,
    val todayXp: Int? = null,
    val todaySeconds: Int? = null,
    val todayMinutes: Int? = null,
    val todayAttempts: Int? = null,
    val streakCurrentDays: Int? = null,
    val unreadNotifications: Int? = null,
    val smartGuideCompletedSteps: Int? = null,
    val smartGuideTotalSteps: Int? = null,
    val smartGuideCompletionPercent: Int? = null,
)

data class ConfirmedSubjectDelta(
    val subjectVersionId: Int,
    val points: Int? = null,
    val level: Int? = null,
    val levelProgressPercent: Int? = null,
    val hearts: Int? = null,
)

data class ConfirmedStudentDelta(
    val studentId: String,
    val profile: ConfirmedProfileDelta? = null,
    val subjects: List<ConfirmedSubjectDelta> = emptyList(),
    val serverVersion: String? = null,
    val confirmedAtEpochMillis: Long,
)

interface StudentLocalStore {
    fun observeProfile(studentId: String): Flow<StudentProfileStateEntity?>
    fun observeSubject(studentId: String, subjectVersionId: Int): Flow<StudentSubjectStateEntity?>
    fun observeSubjects(studentId: String): Flow<List<StudentSubjectStateEntity>>
    fun observeOutstandingOperations(studentId: String): Flow<Int>
    fun observeResumableQuestionSession(studentId: String): Flow<QuestionSessionEntity?>
    fun observeLiveState(studentId: String): Flow<StudentLiveState>

    suspend fun readDocument(
        studentId: String,
        kind: String,
        documentId: String = CachedDocumentEntity.DEFAULT_DOCUMENT_ID,
    ): CachedDocumentEntity?

    suspend fun putDocument(
        studentId: String,
        kind: String,
        payloadJson: String,
        documentId: String = CachedDocumentEntity.DEFAULT_DOCUMENT_ID,
        serverVersion: String? = null,
        savedAtEpochMillis: Long = System.currentTimeMillis(),
    )

    suspend fun replaceProfileState(entity: StudentProfileStateEntity)
    suspend fun replaceSubjectStates(entities: List<StudentSubjectStateEntity>)
    suspend fun deleteDocumentKind(studentId: String, kind: String)
    suspend fun applyConfirmedDelta(delta: ConfirmedStudentDelta)
    suspend fun enqueueOperation(operation: PendingOperationEntity)
    suspend fun markOperationSyncing(operationId: String, nowEpochMillis: Long = System.currentTimeMillis())
    suspend fun markOperationFailed(
        operationId: String,
        error: String?,
        nowEpochMillis: Long = System.currentTimeMillis(),
    )
    suspend fun markOperationConfirmed(operationId: String, nowEpochMillis: Long = System.currentTimeMillis())
    suspend fun confirmOperation(
        operationId: String,
        delta: ConfirmedStudentDelta? = null,
        nowEpochMillis: Long = System.currentTimeMillis(),
    )
    suspend fun recoverInterruptedOperations(
        studentId: String,
        nowEpochMillis: Long = System.currentTimeMillis(),
        staleAfterMillis: Long = 2L * 60L * 1_000L,
    ): Int

    suspend fun readyOperations(
        studentId: String,
        nowEpochMillis: Long = System.currentTimeMillis(),
        limit: Int = 50,
    ): List<PendingOperationEntity>

    suspend fun saveQuestionSession(session: QuestionSessionEntity)
    suspend fun saveQuestionAnswer(answer: QuestionAnswerEntity)
    suspend fun recordQuestionAnswer(
        answer: QuestionAnswerEntity,
        nextQuestionIndex: Int,
        sessionStatus: String,
        operation: PendingOperationEntity,
    )
    suspend fun readQuestionAnswers(studentId: String, sessionId: String): List<QuestionAnswerEntity>
    suspend fun clearStudent(studentId: String)
}

class RoomStudentLocalStore(
    private val database: MasaryLocalDatabase,
) : StudentLocalStore {
    private val stateDao = database.studentStateDao()
    private val documentDao = database.cachedDocumentDao()
    private val operationDao = database.pendingOperationDao()
    private val sessionDao = database.questionSessionDao()

    override fun observeProfile(studentId: String): Flow<StudentProfileStateEntity?> =
        stateDao.observeProfile(studentId)

    override fun observeSubject(studentId: String, subjectVersionId: Int): Flow<StudentSubjectStateEntity?> =
        stateDao.observeSubject(studentId, subjectVersionId)

    override fun observeSubjects(studentId: String): Flow<List<StudentSubjectStateEntity>> =
        stateDao.observeSubjects(studentId)

    override fun observeOutstandingOperations(studentId: String): Flow<Int> =
        operationDao.observeOutstandingCount(studentId)

    override fun observeResumableQuestionSession(studentId: String): Flow<QuestionSessionEntity?> =
        sessionDao.observeResumableSession(studentId)

    override fun observeLiveState(studentId: String): Flow<StudentLiveState> =
        combine(
            stateDao.observeProfile(studentId),
            stateDao.observeSubjects(studentId),
        ) { profile, subjects ->
            StudentLiveState(
                profile = profile?.let {
                    StudentProfileLiveState(
                        studentId = it.studentId,
                        globalXp = it.globalXp,
                        gems = it.gems,
                        level = it.level,
                        levelProgressPercent = it.levelProgressPercent,
                        levelNextXp = it.levelNextXp,
                        todayXp = it.todayXp,
                        todaySeconds = it.todaySeconds,
                        todayMinutes = it.todayMinutes,
                        todayAttempts = it.todayAttempts,
                        streakCurrentDays = it.streakCurrentDays,
                        unreadNotifications = it.unreadNotifications,
                        smartGuideCompletedSteps = it.smartGuideCompletedSteps,
                        smartGuideTotalSteps = it.smartGuideTotalSteps,
                        smartGuideCompletionPercent = it.smartGuideCompletionPercent,
                        serverVersion = it.serverVersion,
                        confirmedAtEpochMillis = it.confirmedAtEpochMillis,
                    )
                },
                subjects = subjects.associate { subject ->
                    subject.subjectVersionId to StudentSubjectLiveState(
                        studentId = subject.studentId,
                        subjectVersionId = subject.subjectVersionId,
                        points = subject.points,
                        level = subject.level,
                        levelProgressPercent = subject.levelProgressPercent,
                        hearts = subject.hearts,
                        serverVersion = subject.serverVersion,
                        confirmedAtEpochMillis = subject.confirmedAtEpochMillis,
                    )
                },
            )
        }

    override suspend fun readDocument(
        studentId: String,
        kind: String,
        documentId: String,
    ): CachedDocumentEntity? = documentDao.read(studentId, kind, documentId)

    override suspend fun putDocument(
        studentId: String,
        kind: String,
        payloadJson: String,
        documentId: String,
        serverVersion: String?,
        savedAtEpochMillis: Long,
    ) {
        require(studentId.isNotBlank()) { "studentId is required" }
        require(kind.isNotBlank()) { "document kind is required" }
        documentDao.upsert(
            CachedDocumentEntity(
                studentId = studentId,
                kind = kind,
                documentId = documentId,
                payloadJson = payloadJson,
                serverVersion = serverVersion,
                savedAtEpochMillis = savedAtEpochMillis,
            ),
        )
    }

    override suspend fun replaceProfileState(entity: StudentProfileStateEntity) {
        require(entity.studentId.isNotBlank()) { "studentId is required" }
        stateDao.upsertProfile(entity)
    }

    override suspend fun replaceSubjectStates(entities: List<StudentSubjectStateEntity>) {
        if (entities.isEmpty()) return
        require(entities.all { it.studentId.isNotBlank() && it.subjectVersionId > 0 }) {
            "valid studentId and subjectVersionId are required"
        }
        stateDao.upsertSubjects(entities)
    }

    override suspend fun deleteDocumentKind(studentId: String, kind: String) {
        require(studentId.isNotBlank()) { "studentId is required" }
        require(kind.isNotBlank()) { "document kind is required" }
        documentDao.deleteKind(studentId, kind)
    }

    override suspend fun applyConfirmedDelta(delta: ConfirmedStudentDelta) {
        require(delta.studentId.isNotBlank()) { "studentId is required" }
        database.withTransaction {
            applyConfirmedDeltaInTransaction(delta)
        }
    }

    private suspend fun applyConfirmedDeltaInTransaction(delta: ConfirmedStudentDelta) {
        delta.profile?.let { patch ->
            val current = stateDao.readProfile(delta.studentId)
                ?: StudentProfileStateEntity(studentId = delta.studentId)
            stateDao.upsertProfile(
                current.copy(
                    globalXp = patch.globalXp?.coerceAtLeast(0) ?: current.globalXp,
                    gems = patch.gems?.coerceAtLeast(0) ?: current.gems,
                    level = patch.level?.coerceAtLeast(1) ?: current.level,
                    levelProgressPercent = patch.levelProgressPercent?.coerceIn(0, 100)
                        ?: current.levelProgressPercent,
                    levelNextXp = patch.levelNextXp?.coerceAtLeast(0) ?: current.levelNextXp,
                    todayXp = patch.todayXp?.coerceAtLeast(0) ?: current.todayXp,
                    todaySeconds = patch.todaySeconds?.coerceAtLeast(0) ?: current.todaySeconds,
                    todayMinutes = patch.todayMinutes?.coerceAtLeast(0) ?: current.todayMinutes,
                    todayAttempts = patch.todayAttempts?.coerceAtLeast(0) ?: current.todayAttempts,
                    streakCurrentDays = patch.streakCurrentDays?.coerceAtLeast(0)
                        ?: current.streakCurrentDays,
                    unreadNotifications = patch.unreadNotifications?.coerceAtLeast(0)
                        ?: current.unreadNotifications,
                    smartGuideCompletedSteps = patch.smartGuideCompletedSteps?.coerceAtLeast(0)
                        ?: current.smartGuideCompletedSteps,
                    smartGuideTotalSteps = patch.smartGuideTotalSteps?.coerceAtLeast(0)
                        ?: current.smartGuideTotalSteps,
                    smartGuideCompletionPercent = patch.smartGuideCompletionPercent?.coerceIn(0, 100)
                        ?: current.smartGuideCompletionPercent,
                    serverVersion = delta.serverVersion ?: current.serverVersion,
                    confirmedAtEpochMillis = delta.confirmedAtEpochMillis,
                ),
            )
        }

        if (delta.subjects.isNotEmpty()) {
            val patched = delta.subjects
                .filter { it.subjectVersionId > 0 }
                .map { patch ->
                    val current = stateDao.readSubject(delta.studentId, patch.subjectVersionId)
                        ?: StudentSubjectStateEntity(
                            studentId = delta.studentId,
                            subjectVersionId = patch.subjectVersionId,
                        )
                    current.copy(
                        points = patch.points?.coerceAtLeast(0) ?: current.points,
                        level = patch.level?.coerceAtLeast(1) ?: current.level,
                        levelProgressPercent = patch.levelProgressPercent?.coerceIn(0, 100)
                            ?: current.levelProgressPercent,
                        hearts = patch.hearts?.coerceAtLeast(0) ?: current.hearts,
                        serverVersion = delta.serverVersion ?: current.serverVersion,
                        confirmedAtEpochMillis = delta.confirmedAtEpochMillis,
                    )
                }
            stateDao.upsertSubjects(patched)
        }
    }

    override suspend fun enqueueOperation(operation: PendingOperationEntity) {
        require(operation.operationId.isNotBlank()) { "operationId is required" }
        require(operation.studentId.isNotBlank()) { "studentId is required" }
        database.withTransaction {
            val existing = operationDao.read(operation.operationId)
            require(existing == null || existing.studentId == operation.studentId) {
                "operationId is already owned by another student"
            }
            operationDao.upsert(operation)
        }
    }

    override suspend fun markOperationSyncing(operationId: String, nowEpochMillis: Long) {
        val operation = operationDao.read(operationId) ?: return
        operationDao.updateState(
            operationId = operationId,
            state = PendingOperationState.SYNCING,
            attemptCount = operation.attemptCount,
            updatedAtEpochMillis = nowEpochMillis,
            nextAttemptAtEpochMillis = 0,
            lastError = null,
        )
    }

    override suspend fun markOperationFailed(
        operationId: String,
        error: String?,
        nowEpochMillis: Long,
    ) {
        val operation = operationDao.read(operationId) ?: return
        val attempts = operation.attemptCount + 1
        operationDao.updateState(
            operationId = operationId,
            state = PendingOperationState.FAILED,
            attemptCount = attempts,
            updatedAtEpochMillis = nowEpochMillis,
            nextAttemptAtEpochMillis = nowEpochMillis +
                PendingOperationRetryPolicy.nextDelayMillis(attempts - 1),
            lastError = error?.take(500),
        )
    }

    override suspend fun markOperationConfirmed(operationId: String, nowEpochMillis: Long) {
        confirmOperation(operationId, null, nowEpochMillis)
    }

    override suspend fun confirmOperation(
        operationId: String,
        delta: ConfirmedStudentDelta?,
        nowEpochMillis: Long,
    ) {
        require(operationId.isNotBlank()) { "operationId is required" }
        database.withTransaction {
            val operation = operationDao.read(operationId) ?: return@withTransaction
            delta?.let {
                require(it.studentId == operation.studentId) {
                    "confirmed delta owner must match operation owner"
                }
                applyConfirmedDeltaInTransaction(it)
            }
            operationDao.updateState(
                operationId = operationId,
                state = PendingOperationState.CONFIRMED,
                attemptCount = operation.attemptCount,
                updatedAtEpochMillis = nowEpochMillis,
                nextAttemptAtEpochMillis = 0,
                lastError = null,
            )
        }
    }

    override suspend fun recoverInterruptedOperations(
        studentId: String,
        nowEpochMillis: Long,
        staleAfterMillis: Long,
    ): Int {
        require(studentId.isNotBlank()) { "studentId is required" }
        return operationDao.recoverInterrupted(
            studentId = studentId,
            staleBeforeEpochMillis = nowEpochMillis - staleAfterMillis.coerceAtLeast(0),
            nowEpochMillis = nowEpochMillis,
        )
    }

    override suspend fun readyOperations(
        studentId: String,
        nowEpochMillis: Long,
        limit: Int,
    ): List<PendingOperationEntity> =
        operationDao.ready(studentId, nowEpochMillis, limit.coerceIn(1, 100))

    override suspend fun saveQuestionSession(session: QuestionSessionEntity) {
        require(session.sessionId.isNotBlank()) { "sessionId is required" }
        require(session.studentId.isNotBlank()) { "studentId is required" }
        database.withTransaction {
            val existing = sessionDao.readSessionById(session.sessionId)
            require(existing == null || existing.studentId == session.studentId) {
                "question session is already owned by another student"
            }
            sessionDao.upsertSession(session)
        }
    }

    override suspend fun saveQuestionAnswer(answer: QuestionAnswerEntity) =
        sessionDao.upsertAnswer(answer)

    override suspend fun recordQuestionAnswer(
        answer: QuestionAnswerEntity,
        nextQuestionIndex: Int,
        sessionStatus: String,
        operation: PendingOperationEntity,
    ) {
        require(answer.studentId == operation.studentId) { "answer and operation owners must match" }
        require(answer.sessionId.isNotBlank()) { "sessionId is required" }
        require(operation.operationId.isNotBlank()) { "operationId is required" }
        database.withTransaction {
            val session = sessionDao.readSession(answer.studentId, answer.sessionId)
                ?: error("Question session is not available locally.")
            require(session.studentId == answer.studentId) { "session owner mismatch" }
            sessionDao.upsertAnswer(answer)
            sessionDao.upsertSession(
                session.copy(
                    currentQuestionIndex = nextQuestionIndex.coerceAtLeast(0),
                    status = sessionStatus,
                    updatedAtEpochMillis = answer.answeredAtEpochMillis,
                    completedAtEpochMillis = if (sessionStatus == "completed_local") {
                        answer.answeredAtEpochMillis
                    } else {
                        session.completedAtEpochMillis
                    },
                ),
            )
            operationDao.upsert(operation)
        }
    }

    override suspend fun readQuestionAnswers(studentId: String, sessionId: String): List<QuestionAnswerEntity> =
        sessionDao.readAnswers(studentId, sessionId)

    override suspend fun clearStudent(studentId: String) {
        database.withTransaction {
            stateDao.deleteProfile(studentId)
            stateDao.deleteSubjects(studentId)
            documentDao.deleteStudent(studentId)
            operationDao.deleteStudent(studentId)
            sessionDao.deleteStudentAnswers(studentId)
            sessionDao.deleteStudentSessions(studentId)
        }
    }
}
