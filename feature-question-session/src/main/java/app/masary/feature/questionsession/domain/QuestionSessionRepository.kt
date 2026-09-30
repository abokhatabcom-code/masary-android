package app.masary.feature.questionsession.domain

data class QuestionAnswerSyncSummary(
    val attempted: Int,
    val confirmed: Int,
    val retryScheduled: Int,
)

interface QuestionSessionRepository {
    suspend fun loadPackage(sessionId: String): Result<QuestionSessionPackage>
    suspend fun loadSnapshot(sessionId: String): QuestionSessionPackage?
    suspend fun saveLocalAnswer(
        sessionId: String,
        questionId: String,
        answer: QuestionAnswerInput,
        nextQuestionIndex: Int,
        completed: Boolean,
    ): Result<Unit>
    suspend fun syncPendingAnswers(): Result<QuestionAnswerSyncSummary>
    suspend fun markCompletedLocal(
        sessionId: String,
        allowIncomplete: Boolean = false,
    ): Result<Unit>
    suspend fun loadActiveSeconds(sessionId: String): Int?
    suspend fun saveActiveSeconds(sessionId: String, seconds: Int): Result<Unit>
    suspend fun loadResult(sessionId: String): QuestionSessionResult?
    suspend fun finishSession(sessionId: String): Result<QuestionSessionResult>
}

open class QuestionSessionException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

class QuestionSessionNetworkException(cause: Throwable? = null) :
    QuestionSessionException("تعذر الاتصال بالخادم. سنعرض الجلسة المحفوظة إن كانت متاحة.", cause)

class QuestionSessionExpiredException(
    message: String = "انتهت صلاحية جلسة النشاط.",
    cause: Throwable? = null,
) : QuestionSessionException(message, cause)

class QuestionSessionNotFoundException(
    message: String = "جلسة النشاط غير متاحة.",
    cause: Throwable? = null,
) : QuestionSessionException(message, cause)

class QuestionSessionSourceUnavailableException(
    message: String = "مصدر أسئلة هذا النشاط غير متاح في البيئة الحالية.",
    cause: Throwable? = null,
) : QuestionSessionException(message, cause)

class QuestionSessionServiceException(
    message: String = "تعذر تحميل أسئلة الجلسة الآن.",
    cause: Throwable? = null,
) : QuestionSessionException(message, cause)
