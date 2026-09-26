package app.masary.feature.subject.domain

data class StudentSubjectPage(
    val studentId: String,
    val subjectVersionId: Int,
    val generatedAt: String,
    val identity: SubjectIdentity,
    val points: SubjectIntValue,
    val level: SubjectIntValue,
    val progress: SubjectProgressValue,
    val hearts: SubjectHearts,
    val access: SubjectAccess,
    val content: SubjectContentSummary,
    val lastActivity: SubjectLastActivity,
    val actions: SubjectActions,
    val version: String,
    val snapshot: SubjectSnapshotMetadata? = null,
)

data class SubjectIdentity(
    val name: String,
    val curriculumLabel: String,
    val versionType: String,
    val media: SubjectMedia,
)

data class SubjectMedia(
    val available: Boolean,
    val key: String?,
    val reason: String,
)

data class SubjectIntValue(
    val available: Boolean,
    val value: Int?,
    val reason: String,
)

data class SubjectProgressValue(
    val available: Boolean,
    val percent: Int?,
    val reason: String,
)

data class SubjectRestoreTime(
    val available: Boolean,
    val at: String?,
    val reason: String,
)

data class SubjectHearts(
    val current: Int,
    val maximum: Int,
    val nextRestore: SubjectRestoreTime,
)

enum class SubjectAccessStatus {
    Unknown,
    Available,
    Free,
    RequiresSubscription,
    Blocked,
}

data class SubjectAccess(
    val available: Boolean,
    val status: SubjectAccessStatus,
    val reason: String,
)

enum class SubjectStructureMode {
    Units,
    Lessons,
    Unknown,
}

data class SubjectContentPart(
    val partNumber: Int,
    val label: String,
    val unitsCount: Int,
    val lessonsCount: Int,
)

enum class SubjectLearningStatus {
    Unknown,
    InProgress,
    Completed,
    Locked,
    Unavailable,
}

data class SubjectLearningState(
    val status: SubjectLearningStatus,
    val reason: String,
)

data class SubjectLesson(
    val id: Int,
    val unitId: Int?,
    val partNumber: Int,
    val title: String,
    val position: Int,
    val state: SubjectLearningState,
    val preparation: SubjectActionAvailability,
)

data class SubjectUnit(
    val id: Int,
    val partNumber: Int,
    val title: String,
    val position: Int,
    val state: SubjectLearningState,
    val lessons: List<SubjectLesson>,
)

data class SubjectContentSummary(
    val structureMode: SubjectStructureMode,
    val hasParts: Boolean,
    val parts: List<SubjectContentPart>,
    val detailsAvailable: Boolean,
    val reason: String,
    val units: List<SubjectUnit> = emptyList(),
    val lessons: List<SubjectLesson> = emptyList(),
)

data class SubjectActionAvailability(
    val available: Boolean,
    val reason: String,
)

data class SubjectLastActivity(
    val available: Boolean,
    val unitId: Int?,
    val mode: String,
    val updatedAt: String,
    val preparation: SubjectActionAvailability,
    val reason: String,
    val lessonId: Int? = null,
)

data class SubjectActions(
    val trainingCenter: SubjectActionAvailability,
)

data class SubjectSnapshotMetadata(val savedAtEpochMillis: Long)
