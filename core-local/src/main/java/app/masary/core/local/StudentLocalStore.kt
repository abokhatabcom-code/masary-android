package app.masary.core.local

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

data class ConfirmedProfileDelta(
    val globalXp: Int? = null,
    val gems: Int? = null,
    val level: Int? = null,
    val levelProgressPercent: Int? = null,
    val levelNextXp: Int? = null,
    val streakCurrentDays: Int? = null,
    val unreadNotifications: Int? = null,
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

class StudentLocalStore(
    private val database: MasaryLocalDatabase,
) {
    private val stateDao = database.studentStateDao()
    private val documentDao = database.cachedDocumentDao()
    private val operationDao = database.pendingOperationDao()
    private val sessionDao = database.questionSessionDao()

    fun observeProfile(studentId: String): Flow<StudentProfileStateEntity?> =
        stateDao.observeProfile(studentId)

    fun observeSubject(studentId: String, subjectVersionId: Int): Flow<StudentSubjectStateEntity?> =
        stateDao.observeSubject(studentId, subjectVersionId)

    fun observeSubjects(studentId: String): Flow<List<StudentSubjectStateEntity>> =
        stateDao.observeSubjects(studentId)

    fun observeOutstandingOperations(studentId: String): Flow<Int> =
        operationDao.observeOutstandingCount(studentId)

    fun observeResumableQuestionSession(studentId: String): Flow<QuestionSessionEntity?> =
        sessionDao.observeResumableSession(studentId)

    suspend fun readDocument(
        studentId: String,
        kind: String,
        documentId: String = CachedDocumentEntity.DEFAULT_DOCUMENT_ID,
    ): CachedDocumentEntity? = documentDao.read(studentId, kind, documentId)

    suspend fun putDocument(
        studentId: String,
        kind: String,
        payloadJson: String,
        documentId: String = CachedDocumentEntity.DEFAULT_DOCUMENT_ID,
        serverVersion: String? = null,
        savedAtEpochMillis: Long = System.currentTimeMillis(),
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

    suspend fun applyConfirmedDelta(delta: ConfirmedStudentDelta) {
        require(delta.studentId.isNotBlank()) { "studentId is required" }
        database.withTransaction {
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
                        streakCurrentDays = patch.streakCurrentDays?.coerceAtLeast(0)
                            ?: current.streakCurrentDays,
                        unreadNotifications = patch.unreadNotifications?.coerceAtLeast(0)
                            ?: current.unreadNotifications,
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
    }

    suspend fun enqueueOperation(operation: PendingOperationEntity) {
        require(operation.operationId.isNotBlank()) { "operationId is required" }
        require(operation.studentId.isNotBlank()) { "studentId is required" }
        operationDao.upsert(operation)
    }

    suspend fun readyOperations(
        studentId: String,
        nowEpochMillis: Long = System.currentTimeMillis(),
        limit: Int = 50,
    ): List<PendingOperationEntity> =
        operationDao.ready(studentId, nowEpochMillis, limit.coerceIn(1, 100))

    suspend fun saveQuestionSession(session: QuestionSessionEntity) =
        sessionDao.upsertSession(session)

    suspend fun saveQuestionAnswer(answer: QuestionAnswerEntity) =
        sessionDao.upsertAnswer(answer)

    suspend fun readQuestionAnswers(studentId: String, sessionId: String): List<QuestionAnswerEntity> =
        sessionDao.readAnswers(studentId, sessionId)

    suspend fun clearStudent(studentId: String) {
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
