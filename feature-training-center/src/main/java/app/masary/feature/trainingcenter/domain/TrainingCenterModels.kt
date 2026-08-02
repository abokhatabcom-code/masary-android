package app.masary.feature.trainingcenter.domain

data class StudentTrainingCenter(
    val studentId: String,
    val subjectVersionId: Int,
    val generatedAt: String,
    val identity: TrainingCenterIdentity,
    val tools: List<TrainingCenterTool>,
    val version: String,
    val snapshot: TrainingCenterSnapshotMetadata? = null,
)

data class TrainingCenterIdentity(
    val name: String,
    val curriculumLabel: String,
)

enum class TrainingToolKey(
    val wireKey: String,
    val activityType: String,
    val activityMode: String,
    val source: String,
    val order: Int,
) {
    Choose("choose", "choose_test", "practice", "subject", 0),
    TrueFalse("truefalse", "true_false_test", "practice", "subject", 1),
    Connect("connect", "connect_test", "practice", "subject", 2),
    Fill("fill", "fill_test", "practice", "subject", 3),
    Speed("speed", "speed_test", "speed", "subject", 4),
    SmartReview("smart_review", "smart_review", "review", "review", 5),
    ;

    companion object {
        val ordered: List<TrainingToolKey> = entries.sortedBy(TrainingToolKey::order)

        fun fromWire(value: String): TrainingToolKey? =
            entries.firstOrNull { it.wireKey == value.trim().lowercase() }
    }
}

enum class TrainingToolStatus {
    Ready,
    Empty,
    SourceUnavailable,
}

data class TrainingCenterTool(
    val key: TrainingToolKey,
    val title: String,
    val description: String,
    val available: Boolean,
    val status: TrainingToolStatus,
    val reason: String,
    val itemCount: Int?,
)

data class TrainingCenterSnapshotMetadata(val savedAtEpochMillis: Long)
