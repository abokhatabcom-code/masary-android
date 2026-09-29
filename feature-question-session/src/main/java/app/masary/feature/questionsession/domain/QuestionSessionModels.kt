package app.masary.feature.questionsession.domain

enum class QuestionType(val wireValue: String) {
    Choose("choose"),
    TrueFalse("truefalse"),
    Connect("connect"),
    Fill("fill"),
    Speed("speed"),
    ;

    companion object {
        fun fromWire(value: String): QuestionType? =
            entries.firstOrNull { it.wireValue == value.trim().lowercase() }
    }
}

data class QuestionOption(
    val id: String,
    val text: String,
)

data class ConnectItem(
    val id: String,
    val text: String,
)

sealed interface QuestionPayload {
    data class Options(val options: List<QuestionOption>) : QuestionPayload
    data class Fill(val inputMode: String = "text") : QuestionPayload
    data class Connect(
        val leftItems: List<ConnectItem>,
        val rightItems: List<ConnectItem>,
    ) : QuestionPayload
}

data class QuestionItem(
    val id: String,
    val type: QuestionType,
    val prompt: String,
    val payload: QuestionPayload,
)

data class QuestionSessionInfo(
    val id: String,
    val status: String,
    val expiresAt: String,
    val subjectVersionId: Int,
    val unitId: Int?,
    val lessonId: Int?,
    val activityType: String,
    val activityMode: String,
)

data class QuestionSessionProgress(
    val currentIndex: Int,
    val totalQuestions: Int,
)

data class QuestionResultPolicy(
    val showPassBadge: Boolean = true,
    val showScore: Boolean = true,
    val showCountsCorrect: Boolean = true,
    val showCountsPartial: Boolean = true,
    val showCountsWrong: Boolean = true,
    val showXp: Boolean = true,
    val showHeartsSpent: Boolean = true,
    val showRetryButton: Boolean = true,
    val showBackButton: Boolean = true,
    val showReviewDetails: Boolean = true,
    val showMistakesButton: Boolean = true,
)

data class QuestionActiveTimePolicy(
    val enabled: Boolean = true,
    val idleSeconds: Int = 45,
    val pingInterval: Int = 15,
)

data class QuestionSessionPolicy(
    val allowBack: Boolean = true,
    val allowSkip: Boolean = true,
    val revealAnswers: Boolean = true,
    val tfReasonOnlyOnFalse: Boolean = true,
    val passPercent: Int = 60,
    val timerSeconds: Int = 0,
    val activeTime: QuestionActiveTimePolicy = QuestionActiveTimePolicy(),
    val result: QuestionResultPolicy = QuestionResultPolicy(),
)

data class QuestionSessionPackage(
    val version: String,
    val generatedAt: String,
    val session: QuestionSessionInfo,
    val progress: QuestionSessionProgress,
    val policy: QuestionSessionPolicy = QuestionSessionPolicy(),
    val questions: List<QuestionItem>,
    val snapshotSavedAtEpochMillis: Long? = null,
)


data class ConnectAnswerPair(
    val leftId: String,
    val rightId: String,
)

sealed interface QuestionAnswerInput {
    data class Choice(val optionId: String) : QuestionAnswerInput
    data class Text(val value: String) : QuestionAnswerInput
    data class Connections(val pairs: List<ConnectAnswerPair>) : QuestionAnswerInput
}


data class QuestionSessionScore(
    val correctAnswers: Int,
    val partialAnswers: Int = 0,
    val incorrectAnswers: Int,
    val totalQuestions: Int,
    val scorePercent: Int,
    val passed: Boolean = false,
    val passPercent: Int = 60,
    val xpEarned: Double = 0.0,
    val heartsSpent: Int = 0,
)

data class QuestionSessionResult(
    val sessionId: String,
    val completedAt: String,
    val replayed: Boolean,
    val score: QuestionSessionScore,
    val policy: QuestionSessionPolicy = QuestionSessionPolicy(),
    val confirmedDeltaAvailable: Boolean,
    val confirmedDeltaReason: String,
)
