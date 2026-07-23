package app.masary.core.network.home

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Header

data class StudentHomeResponseDto(
    val success: Boolean,
    val data: StudentHomeDataDto? = null,
    val error: StudentHomeErrorDto? = null,
)

data class StudentHomeErrorDto(
    val code: String = "",
    val message: String = "",
)

data class StudentHomeDataDto(
    val version: String = "",
    @SerializedName("generated_at") val generatedAt: String = "",
    val student: HomeStudentDto = HomeStudentDto(),
    val summary: HomeSummaryDto = HomeSummaryDto(),
    val streak: HomeStreakDto = HomeStreakDto(),
    val today: HomeTodayDto = HomeTodayDto(),
    val subscription: HomeSubscriptionDto = HomeSubscriptionDto(),
    val notifications: HomeNotificationsDto = HomeNotificationsDto(),
    @SerializedName("continue_learning") val continueLearning: HomeContinueLearningDto = HomeContinueLearningDto(),
    @SerializedName("smart_guide") val smartGuide: HomeSmartGuideDto = HomeSmartGuideDto(),
    val indicators: HomeIndicatorsDto = HomeIndicatorsDto(),
    val subjects: List<HomeSubjectDto> = emptyList(),
    val spotlight: HomeSpotlightDto? = null,
)

data class HomeIndicatorsDto(
    @SerializedName("total_xp") val totalXp: Int = 0,
    val gems: Int = 0,
    @SerializedName("streak_days") val streakDays: Int = 0,
    @SerializedName("global_rank") val globalRank: Int? = null,
)

data class HomeSubjectDto(
    @SerializedName("subject_version_id") val subjectVersionId: Int = 0,
    val name: String = "",
    val hearts: Int = 0,
    @SerializedName("progress_percent") val progressPercent: Int = 0,
)

data class HomeSpotlightDto(
    val type: String = "news",
    val title: String = "",
    val body: String = "",
    @SerializedName("cta_label") val ctaLabel: String = "",
    @SerializedName("cta_url") val ctaUrl: String = "",
)

data class HomeStudentDto(
    val id: String = "",
    val username: String = "",
    @SerializedName("display_name") val displayName: String = "",
    @SerializedName("avatar_path") val avatarPath: String? = null,
)

data class HomeSummaryDto(
    @SerializedName("global_xp") val globalXp: Int = 0,
    val gems: Int = 0,
    val level: Int = 1,
    @SerializedName("level_percent") val levelPercent: Int = 0,
    @SerializedName("level_next_xp") val levelNextXp: Int = 300,
)

data class HomeStreakDto(
    @SerializedName("current_days") val currentDays: Int = 0,
    @SerializedName("best_days") val bestDays: Int = 0,
    @SerializedName("protection_count") val protectionCount: Int = 0,
    @SerializedName("protection_max") val protectionMax: Int = 0,
    @SerializedName("next_milestone") val nextMilestone: Int = 0,
    @SerializedName("checkpoint_days") val checkpointDays: Int = 0,
    val status: String = "start",
    val message: String = "",
    val goal: HomeStreakGoalDto = HomeStreakGoalDto(),
)

data class HomeStreakGoalDto(
    val days: Int = 0,
    val status: String = "none",
    @SerializedName("is_completed") val isCompleted: Boolean = false,
    @SerializedName("remaining_days") val remainingDays: Int = 0,
    @SerializedName("progress_percent") val progressPercent: Int = 0,
    val gems: Int = 0,
    val shields: Int = 0,
    val label: String = "",
)

data class HomeTodayDto(
    val xp: Int = 0,
    val seconds: Int = 0,
    val minutes: Int = 0,
    val attempts: Int = 0,
)

data class HomeSubscriptionDto(
    val active: Boolean = false,
    val status: String = "غير نشط",
    @SerializedName("ends_at") val endsAt: String = "",
)

data class HomeNotificationsDto(
    @SerializedName("unread_count") val unreadCount: Int = 0,
)

data class HomeContinueLearningDto(
    val available: Boolean = false,
    @SerializedName("subject_version_id") val subjectVersionId: Int? = null,
    @SerializedName("subject_name") val subjectName: String = "",
    @SerializedName("unit_id") val unitId: Int? = null,
    @SerializedName("unit_title") val unitTitle: String = "",
    val mode: String = "learn",
    val label: String = "ابدأ من المواد",
    val hint: String = "",
    val disabled: Boolean = false,
    @SerializedName("disabled_reason") val disabledReason: String = "",
    val hearts: Int? = null,
    @SerializedName("updated_at") val updatedAt: String = "",
)

data class HomeSmartGuideDto(
    val enabled: Boolean = false,
    val status: String = "disabled",
    @SerializedName("guide_id") val guideId: Int? = null,
    @SerializedName("guide_date") val guideDate: String = "",
    val headline: String = "",
    @SerializedName("intro_text") val introText: String = "",
    @SerializedName("boost_note") val boostNote: String = "",
    @SerializedName("profile_key") val profileKey: String = "",
    @SerializedName("completion_percent") val completionPercent: Int = 0,
    @SerializedName("completed_steps") val completedSteps: Int = 0,
    @SerializedName("total_steps") val totalSteps: Int = 0,
    @SerializedName("is_complete") val isComplete: Boolean = false,
    @SerializedName("updated_at") val updatedAt: String = "",
    val steps: List<HomeSmartGuideStepDto> = emptyList(),
)

data class HomeSmartGuideStepDto(
    val id: Int = 0,
    @SerializedName("sort_order") val sortOrder: Int = 0,
    @SerializedName("subject_version_id") val subjectVersionId: Int = 0,
    @SerializedName("subject_name") val subjectName: String = "",
    @SerializedName("unit_id") val unitId: Int? = null,
    val part: Int = 0,
    @SerializedName("action_key") val actionKey: String = "",
    @SerializedName("action_group") val actionGroup: String = "primary",
    val title: String = "",
    val subtitle: String = "",
    @SerializedName("reason_text") val reasonText: String = "",
    @SerializedName("cta_label") val ctaLabel: String = "ابدأ الآن",
    @SerializedName("estimated_minutes") val estimatedMinutes: Int = 0,
    @SerializedName("reward_gems") val rewardGems: Int = 0,
    @SerializedName("progress_state") val progressState: String = "pending",
    @SerializedName("completed_at") val completedAt: String = "",
)

interface StudentHomeApi {
    @GET("/api/v1/student/home")
    suspend fun home(@Header("Authorization") authorization: String): StudentHomeResponseDto
}
