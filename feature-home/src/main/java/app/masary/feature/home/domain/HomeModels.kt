package app.masary.feature.home.domain

data class StudentHomeData(
    val version: String,
    val generatedAt: String,
    val student: HomeStudent,
    val summary: HomeSummary,
    val streak: HomeStreak,
    val today: HomeToday,
    val subscription: HomeSubscription,
    val notifications: HomeNotifications,
    val continueLearning: HomeContinueLearning,
    val smartGuide: HomeSmartGuide,
)

data class HomeStudent(
    val id: String,
    val username: String,
    val displayName: String,
    val avatarPath: String?,
)

data class HomeSummary(
    val globalXp: Int,
    val gems: Int,
    val level: Int,
    val levelPercent: Int,
    val levelNextXp: Int,
)

data class HomeStreak(
    val currentDays: Int,
    val bestDays: Int,
    val protectionCount: Int,
    val protectionMax: Int,
    val nextMilestone: Int,
    val checkpointDays: Int,
    val status: String,
    val message: String,
    val goal: HomeStreakGoal,
)

data class HomeStreakGoal(
    val days: Int,
    val status: String,
    val isCompleted: Boolean,
    val remainingDays: Int,
    val progressPercent: Int,
    val gems: Int,
    val shields: Int,
    val label: String,
)

data class HomeToday(
    val xp: Int,
    val seconds: Int,
    val minutes: Int,
    val attempts: Int,
)

data class HomeSubscription(
    val active: Boolean,
    val status: String,
    val endsAt: String,
)

data class HomeNotifications(val unreadCount: Int)

data class HomeContinueLearning(
    val available: Boolean,
    val subjectVersionId: Int?,
    val subjectName: String,
    val unitId: Int?,
    val unitTitle: String,
    val mode: String,
    val label: String,
    val hint: String,
    val disabled: Boolean,
    val disabledReason: String,
    val hearts: Int?,
    val updatedAt: String,
)

data class HomeSmartGuide(
    val enabled: Boolean,
    val status: String,
    val guideId: Int?,
    val guideDate: String,
    val headline: String,
    val introText: String,
    val boostNote: String,
    val profileKey: String,
    val completionPercent: Int,
    val completedSteps: Int,
    val totalSteps: Int,
    val isComplete: Boolean,
    val updatedAt: String,
    val steps: List<HomeSmartGuideStep>,
) {
    val nextPendingStep: HomeSmartGuideStep?
        get() = steps.firstOrNull { it.progressState != "completed" }
}

data class HomeSmartGuideStep(
    val id: Int,
    val sortOrder: Int,
    val subjectVersionId: Int,
    val subjectName: String,
    val unitId: Int?,
    val part: Int,
    val actionKey: String,
    val actionGroup: String,
    val title: String,
    val subtitle: String,
    val reasonText: String,
    val ctaLabel: String,
    val estimatedMinutes: Int,
    val rewardGems: Int,
    val progressState: String,
    val completedAt: String,
)
