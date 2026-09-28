package app.masary.core.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface StudentStateDao {
    @Query("SELECT * FROM student_profile_state WHERE studentId = :studentId LIMIT 1")
    fun observeProfile(studentId: String): Flow<StudentProfileStateEntity?>

    @Query("SELECT * FROM student_profile_state WHERE studentId = :studentId LIMIT 1")
    suspend fun readProfile(studentId: String): StudentProfileStateEntity?

    @Upsert
    suspend fun upsertProfile(entity: StudentProfileStateEntity)

    @Query("SELECT * FROM student_subject_state WHERE studentId = :studentId AND subjectVersionId = :subjectVersionId LIMIT 1")
    fun observeSubject(studentId: String, subjectVersionId: Int): Flow<StudentSubjectStateEntity?>

    @Query("SELECT * FROM student_subject_state WHERE studentId = :studentId AND subjectVersionId = :subjectVersionId LIMIT 1")
    suspend fun readSubject(studentId: String, subjectVersionId: Int): StudentSubjectStateEntity?

    @Query("SELECT * FROM student_subject_state WHERE studentId = :studentId ORDER BY subjectVersionId")
    fun observeSubjects(studentId: String): Flow<List<StudentSubjectStateEntity>>

    @Upsert
    suspend fun upsertSubjects(entities: List<StudentSubjectStateEntity>)

    @Query("DELETE FROM student_profile_state WHERE studentId = :studentId")
    suspend fun deleteProfile(studentId: String)

    @Query("DELETE FROM student_subject_state WHERE studentId = :studentId")
    suspend fun deleteSubjects(studentId: String)
}

@Dao
interface CachedDocumentDao {
    @Query("SELECT * FROM cached_documents WHERE studentId = :studentId AND kind = :kind AND documentId = :documentId LIMIT 1")
    suspend fun read(studentId: String, kind: String, documentId: String): CachedDocumentEntity?

    @Query("SELECT * FROM cached_documents WHERE studentId = :studentId AND kind = :kind AND documentId = :documentId LIMIT 1")
    fun observe(studentId: String, kind: String, documentId: String): Flow<CachedDocumentEntity?>

    @Upsert
    suspend fun upsert(entity: CachedDocumentEntity)

    @Query("DELETE FROM cached_documents WHERE studentId = :studentId")
    suspend fun deleteStudent(studentId: String)

    @Query("DELETE FROM cached_documents WHERE studentId = :studentId AND kind = :kind")
    suspend fun deleteKind(studentId: String, kind: String)
}

@Dao
interface PendingOperationDao {
    @Upsert
    suspend fun upsert(entity: PendingOperationEntity)

    @Query("SELECT * FROM pending_operations WHERE operationId = :operationId LIMIT 1")
    suspend fun read(operationId: String): PendingOperationEntity?

    @Query("""
        SELECT * FROM pending_operations
        WHERE studentId = :studentId
          AND state IN ('pending', 'failed')
          AND nextAttemptAtEpochMillis <= :nowEpochMillis
        ORDER BY createdAtEpochMillis ASC
        LIMIT :limit
    """)
    suspend fun ready(
        studentId: String,
        nowEpochMillis: Long,
        limit: Int,
    ): List<PendingOperationEntity>

    @Query("SELECT COUNT(*) FROM pending_operations WHERE studentId = :studentId AND state IN ('pending', 'syncing', 'failed')")
    fun observeOutstandingCount(studentId: String): Flow<Int>

    @Query("""
        UPDATE pending_operations
        SET state = :state,
            attemptCount = :attemptCount,
            updatedAtEpochMillis = :updatedAtEpochMillis,
            nextAttemptAtEpochMillis = :nextAttemptAtEpochMillis,
            lastError = :lastError
        WHERE operationId = :operationId
    """)
    suspend fun updateState(
        operationId: String,
        state: String,
        attemptCount: Int,
        updatedAtEpochMillis: Long,
        nextAttemptAtEpochMillis: Long,
        lastError: String?,
    )

    @Query("""
        UPDATE pending_operations
        SET state = 'pending',
            updatedAtEpochMillis = :nowEpochMillis,
            nextAttemptAtEpochMillis = :nowEpochMillis,
            lastError = CASE
                WHEN lastError IS NULL OR lastError = '' THEN 'interrupted_sync'
                ELSE lastError
            END
        WHERE studentId = :studentId
          AND state = 'syncing'
          AND updatedAtEpochMillis <= :staleBeforeEpochMillis
    """)
    suspend fun recoverInterrupted(
        studentId: String,
        staleBeforeEpochMillis: Long,
        nowEpochMillis: Long,
    ): Int

    @Query("DELETE FROM pending_operations WHERE operationId = :operationId")
    suspend fun delete(operationId: String)

    @Query("DELETE FROM pending_operations WHERE studentId = :studentId")
    suspend fun deleteStudent(studentId: String)
}

@Dao
interface QuestionSessionDao {
    @Upsert
    suspend fun upsertSession(entity: QuestionSessionEntity)

    @Query("SELECT * FROM question_sessions WHERE studentId = :studentId AND sessionId = :sessionId LIMIT 1")
    suspend fun readSession(studentId: String, sessionId: String): QuestionSessionEntity?

    @Query("SELECT * FROM question_sessions WHERE sessionId = :sessionId LIMIT 1")
    suspend fun readSessionById(sessionId: String): QuestionSessionEntity?

    @Query("SELECT * FROM question_sessions WHERE studentId = :studentId AND status IN ('ready', 'in_progress', 'completed_local') ORDER BY updatedAtEpochMillis DESC LIMIT 1")
    fun observeResumableSession(studentId: String): Flow<QuestionSessionEntity?>

    @Upsert
    suspend fun upsertAnswer(entity: QuestionAnswerEntity)

    @Query("SELECT * FROM question_answers WHERE studentId = :studentId AND sessionId = :sessionId ORDER BY localSequence ASC")
    suspend fun readAnswers(studentId: String, sessionId: String): List<QuestionAnswerEntity>

    @Query("DELETE FROM question_answers WHERE studentId = :studentId")
    suspend fun deleteStudentAnswers(studentId: String)

    @Query("DELETE FROM question_sessions WHERE studentId = :studentId")
    suspend fun deleteStudentSessions(studentId: String)
}
