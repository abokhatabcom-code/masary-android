package app.masary.core.network.activity

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

data class ActivityPreparationRequestDto(
    @SerializedName("subject_version_id") val subjectVersionId: Int,
    @SerializedName("unit_id") val unitId: Int? = null,
    @SerializedName("lesson_id") val lessonId: Int? = null,
    @SerializedName("activity_type") val activityType: String,
    @SerializedName("activity_mode") val activityMode: String,
    @SerializedName("guide_step_id") val guideStepId: Int? = null,
    val source: String,
)

data class ActivityApiErrorDto(val code: String = "", val message: String = "")

data class ActivityDescriptorDto(
    @SerializedName("subject_version_id") val subjectVersionId: Int = 0,
    @SerializedName("subject_name") val subjectName: String = "",
    @SerializedName("unit_id") val unitId: Int? = null,
    @SerializedName("unit_title") val unitTitle: String = "",
    @SerializedName("lesson_id") val lessonId: Int? = null,
    @SerializedName("lesson_title") val lessonTitle: String = "",
    @SerializedName("activity_type") val activityType: String = "",
    @SerializedName("activity_mode") val activityMode: String = "",
    val title: String = "",
    @SerializedName("estimated_minutes") val estimatedMinutes: Int? = null,
    @SerializedName("question_count") val questionCount: Int? = null,
)

data class ActivityEligibilityDto(
    val available: Boolean = false,
    val status: String = "unavailable",
    val reason: String = "",
    @SerializedName("reason_code") val reasonCode: String = "",
)

data class ActivityBalancesDto(val hearts: Int = 0, val gems: Int = 0)

data class ActivityCostDto(
    @SerializedName("required_hearts") val requiredHearts: Int = 0,
    @SerializedName("heart_cost") val heartCost: Int = 0,
    @SerializedName("gem_cost") val gemCost: Int = 0,
)

data class ActivityAttemptsDto(
    val available: Boolean = false,
    val used: Int? = null,
    val remaining: Int? = null,
    val maximum: Int? = null,
    val reason: String = "",
)

data class ActivityResumeDto(
    val available: Boolean = false,
    @SerializedName("session_id") val sessionId: String? = null,
    val status: String = "",
    @SerializedName("expires_at") val expiresAt: String = "",
    val reason: String = "",
)

data class ActivityPreparationPreviewDataDto(
    val version: String = "",
    @SerializedName("generated_at") val generatedAt: String = "",
    val activity: ActivityDescriptorDto = ActivityDescriptorDto(),
    val eligibility: ActivityEligibilityDto = ActivityEligibilityDto(),
    val balances: ActivityBalancesDto = ActivityBalancesDto(),
    val cost: ActivityCostDto = ActivityCostDto(),
    val attempts: ActivityAttemptsDto = ActivityAttemptsDto(),
    val resume: ActivityResumeDto = ActivityResumeDto(),
)

data class ActivityPreparationPreviewResponseDto(
    val success: Boolean,
    val data: ActivityPreparationPreviewDataDto? = null,
    val error: ActivityApiErrorDto? = null,
)

data class ActivityDebitDto(
    @SerializedName("heart_debited") val heartDebited: Int = 0,
    @SerializedName("gems_debited") val gemsDebited: Int = 0,
)

data class ActivityStartDataDto(
    @SerializedName("session_id") val sessionId: String = "",
    val status: String = "",
    val destination: String = "",
    val replayed: Boolean = false,
    val debit: ActivityDebitDto = ActivityDebitDto(),
    val balances: ActivityBalancesDto = ActivityBalancesDto(),
    @SerializedName("expires_at") val expiresAt: String = "",
)

data class ActivityStartResponseDto(
    val success: Boolean,
    val data: ActivityStartDataDto? = null,
    val error: ActivityApiErrorDto? = null,
)

interface StudentActivityPreparationApi {
    @POST("/api/v1/student/activity/preview")
    suspend fun preview(
        @Header("Authorization") authorization: String,
        @Body request: ActivityPreparationRequestDto,
    ): ActivityPreparationPreviewResponseDto

    @POST("/api/v1/student/activity/start")
    suspend fun start(
        @Header("Authorization") authorization: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: ActivityPreparationRequestDto,
    ): ActivityStartResponseDto

    @GET("/api/v1/student/activity/start-status")
    suspend fun startStatus(
        @Header("Authorization") authorization: String,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): ActivityStartResponseDto
}
