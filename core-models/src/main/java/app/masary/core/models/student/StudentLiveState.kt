package app.masary.core.models.student

data class StudentLiveState(
    val profile: StudentProfileLiveState? = null,
    val subjects: Map<Int, StudentSubjectLiveState> = emptyMap(),
)

data class StudentProfileLiveState(
    val studentId: String,
    val globalXp: Int,
    val gems: Int,
    val level: Int,
    val levelProgressPercent: Int,
    val levelNextXp: Int,
    val todayXp: Int,
    val todaySeconds: Int,
    val todayMinutes: Int,
    val todayAttempts: Int,
    val streakCurrentDays: Int,
    val unreadNotifications: Int,
    val smartGuideCompletedSteps: Int,
    val smartGuideTotalSteps: Int,
    val smartGuideCompletionPercent: Int,
    val serverVersion: String?,
    val confirmedAtEpochMillis: Long,
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
