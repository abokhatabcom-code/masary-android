package app.masary.core.models.student

data class StudentLiveState(
    val profile: StudentProfileLiveState? = null,
    val subjects: Map<Int, StudentSubjectLiveState> = emptyMap(),
)

data class StudentProfileLiveState(
    val studentId: String,
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
    val serverVersion: String? = null,
    val confirmedAtEpochMillis: Long = 0,
)

data class StudentSubjectLiveState(
    val studentId: String,
    val subjectVersionId: Int,
    val points: Int?,
    val level: Int?,
    val levelProgressPercent: Int?,
    val hearts: Int?,
    val serverVersion: String?,
    val confirmedAtEpochMillis: Long,
)
