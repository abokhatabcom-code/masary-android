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

data class QuestionSessionPackage(
    val version: String,
    val generatedAt: String,
    val session: QuestionSessionInfo,
    val progress: QuestionSessionProgress,
    val questions: List<QuestionItem>,
    val snapshotSavedAtEpochMillis: Long? = null,
)
