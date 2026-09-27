package app.masary.core.local

import androidx.room.Entity
import androidx.room.Index

@Entity(tableName = "student_profile_state")
data class StudentProfileStateEntity(
    @androidx.room.PrimaryKey val studentId: String,
    val globalXp: Int = 0,
    val gems: Int = 0,
    val level: Int = 1,
    val levelProgressPercent: Int = 0,
    val levelNextXp: Int = 0,
    val streakCurrentDays: Int = 0,
    val unreadNotifications: Int = 0,
    val serverVersion: String? = null,
    val confirmedAtEpochMillis: Long = 0,
)

@Entity(
    tableName = "student_subject_state",
    primaryKeys = ["studentId", "subjectVersionId"],
    indices = [Index("studentId")],
)
data class StudentSubjectStateEntity(
    val studentId: String,
    val subjectVersionId: Int,
    val points: Int? = null,
    val level: Int? = null,
    val levelProgressPercent: Int? = null,
    val hearts: Int? = null,
    val serverVersion: String? = null,
    val confirmedAtEpochMillis: Long = 0,
)

@Entity(
    tableName = "cached_documents",
    primaryKeys = ["studentId", "kind", "documentId"],
    indices = [Index("studentId"), Index(value = ["studentId", "kind"])],
)
data class CachedDocumentEntity(
    val studentId: String,
    val kind: String,
    val documentId: String = DEFAULT_DOCUMENT_ID,
    val payloadJson: String,
    val serverVersion: String? = null,
    val savedAtEpochMillis: Long,
) {
    companion object {
        const val DEFAULT_DOCUMENT_ID = "default"
    }
}

@Entity(
    tableName = "pending_operations",
    indices = [
        Index("studentId"),
        Index(value = ["studentId", "state", "nextAttemptAtEpochMillis"]),
    ],
)
data class PendingOperationEntity(
    @androidx.room.PrimaryKey val operationId: String,
    val studentId: String,
    val type: String,
    val payloadJson: String,
    val state: String = PendingOperationState.PENDING,
    val attemptCount: Int = 0,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val nextAttemptAtEpochMillis: Long = 0,
    val lastError: String? = null,
)

object PendingOperationState {
    const val PENDING = "pending"
    const val SYNCING = "syncing"
    const val CONFIRMED = "confirmed"
    const val FAILED = "failed"
}

@Entity(
    tableName = "question_sessions",
    indices = [Index("studentId"), Index(value = ["studentId", "status"])],
)
data class QuestionSessionEntity(
    @androidx.room.PrimaryKey val sessionId: String,
    val studentId: String,
    val subjectVersionId: Int,
    val unitId: Int? = null,
    val lessonId: Int? = null,
    val status: String,
    val packageJson: String,
    val currentQuestionIndex: Int = 0,
    val serverVersion: String? = null,
    val startedAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val completedAtEpochMillis: Long? = null,
)

@Entity(
    tableName = "question_answers",
    primaryKeys = ["studentId", "sessionId", "questionId"],
    indices = [Index(value = ["studentId", "sessionId"])],
)
data class QuestionAnswerEntity(
    val studentId: String,
    val sessionId: String,
    val questionId: String,
    val answerJson: String,
    val localSequence: Int,
    val answeredAtEpochMillis: Long,
    val syncState: String = PendingOperationState.PENDING,
)
