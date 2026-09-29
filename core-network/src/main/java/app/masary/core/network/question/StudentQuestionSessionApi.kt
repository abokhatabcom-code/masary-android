package app.masary.core.network.question

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

data class QuestionSessionApiErrorDto(
    val code: String = "",
    val message: String = "",
)

data class QuestionSessionDescriptorDto(
    val id: String = "",
    val status: String = "",
    @SerializedName("expires_at") val expiresAt: String = "",
    @SerializedName("started_at") val startedAt: String = "",
    @SerializedName("started_at_epoch_seconds") val startedAtEpochSeconds: Long = 0,
    @SerializedName("subject_version_id") val subjectVersionId: Int = 0,
    @SerializedName("unit_id") val unitId: Int? = null,
    @SerializedName("lesson_id") val lessonId: Int? = null,
    @SerializedName("activity_type") val activityType: String = "",
    @SerializedName("activity_mode") val activityMode: String = "",
)

data class QuestionSessionProgressDto(
    @SerializedName("current_index") val currentIndex: Int = 0,
    @SerializedName("total_questions") val totalQuestions: Int = 0,
)

data class QuestionSessionQuestionDto(
    val id: String = "",
    val type: String = "",
    val prompt: String = "",
    val payload: JsonObject = JsonObject(),
)

data class QuestionResultPolicyDto(
    @SerializedName("show_pass_badge") val showPassBadge: Boolean = true,
    @SerializedName("show_score") val showScore: Boolean = true,
    @SerializedName("show_counts_correct") val showCountsCorrect: Boolean = true,
    @SerializedName("show_counts_partial") val showCountsPartial: Boolean = true,
    @SerializedName("show_counts_wrong") val showCountsWrong: Boolean = true,
    @SerializedName("show_xp") val showXp: Boolean = true,
    @SerializedName("show_hearts_spent") val showHeartsSpent: Boolean = true,
    @SerializedName("show_retry_button") val showRetryButton: Boolean = true,
    @SerializedName("show_back_button") val showBackButton: Boolean = true,
    @SerializedName("show_review_details") val showReviewDetails: Boolean = true,
    @SerializedName("show_mistakes_button") val showMistakesButton: Boolean = true,
)

data class QuestionActiveTimePolicyDto(
    val enabled: Boolean = true,
    @SerializedName("idle_seconds") val idleSeconds: Int = 45,
    @SerializedName("ping_interval") val pingInterval: Int = 15,
)

data class QuestionSessionPolicyDto(
    @SerializedName("allow_back") val allowBack: Boolean = true,
    @SerializedName("allow_skip") val allowSkip: Boolean = true,
    @SerializedName("reveal_answers") val revealAnswers: Boolean = true,
    @SerializedName("tf_reason_only_on_false") val tfReasonOnlyOnFalse: Boolean = true,
    @SerializedName("pass_percent") val passPercent: Int = 60,
    @SerializedName("timer_seconds") val timerSeconds: Int = 0,
    @SerializedName("active_time") val activeTime: QuestionActiveTimePolicyDto =
        QuestionActiveTimePolicyDto(),
    val result: QuestionResultPolicyDto = QuestionResultPolicyDto(),
)

data class QuestionSessionPackageDataDto(
    val version: String = "",
    @SerializedName("generated_at") val generatedAt: String = "",
    val session: QuestionSessionDescriptorDto = QuestionSessionDescriptorDto(),
    val progress: QuestionSessionProgressDto = QuestionSessionProgressDto(),
    val policy: QuestionSessionPolicyDto = QuestionSessionPolicyDto(),
    val questions: List<QuestionSessionQuestionDto> = emptyList(),
)

data class QuestionSessionPackageResponseDto(
    val success: Boolean = false,
    val data: QuestionSessionPackageDataDto? = null,
    val error: QuestionSessionApiErrorDto? = null,
    @SerializedName("request_id") val requestId: String = "",
)

data class QuestionAnswerRequestDto(
    @SerializedName("session_id") val sessionId: String,
    @SerializedName("question_id") val questionId: String,
    val answer: JsonObject,
)

data class QuestionAnswerProgressDto(
    val answered: Int = 0,
    @SerializedName("total_questions") val totalQuestions: Int = 0,
    @SerializedName("all_answered") val allAnswered: Boolean = false,
)

data class QuestionAnswerResultDto(
    @SerializedName("session_id") val sessionId: String = "",
    @SerializedName("question_id") val questionId: String = "",
    val accepted: Boolean = false,
    val correct: Boolean = false,
    val replayed: Boolean = false,
    val progress: QuestionAnswerProgressDto = QuestionAnswerProgressDto(),
)

data class QuestionAnswerResponseDto(
    val success: Boolean = false,
    val data: QuestionAnswerResultDto? = null,
    val error: QuestionSessionApiErrorDto? = null,
    @SerializedName("request_id") val requestId: String = "",
)

data class QuestionFinishRequestDto(
    @SerializedName("session_id") val sessionId: String,
    @SerializedName("active_seconds") val activeSeconds: Int? = null,
)

data class QuestionSessionScoreDto(
    @SerializedName("correct_answers") val correctAnswers: Int = 0,
    @SerializedName("partial_answers") val partialAnswers: Int = 0,
    @SerializedName("incorrect_answers") val incorrectAnswers: Int = 0,
    @SerializedName("total_questions") val totalQuestions: Int = 0,
    @SerializedName("score_percent") val scorePercent: Int = 0,
    val passed: Boolean = false,
    @SerializedName("pass_percent") val passPercent: Int = 60,
    @SerializedName("xp_earned") val xpEarned: Double = 0.0,
    @SerializedName("hearts_spent") val heartsSpent: Int = 0,
    @SerializedName("timed_out") val timedOut: Boolean = false,
    @SerializedName("duration_seconds") val durationSeconds: Int = 0,
    @SerializedName("attempt_id") val attemptId: Int? = null,
)

data class QuestionConfirmedProfileDeltaDto(
    @SerializedName("global_xp") val globalXp: Int? = null,
    val gems: Int? = null,
    val level: Int? = null,
    @SerializedName("level_progress_percent") val levelProgressPercent: Int? = null,
    @SerializedName("level_next_xp") val levelNextXp: Int? = null,
    @SerializedName("today_xp") val todayXp: Int? = null,
    @SerializedName("today_seconds") val todaySeconds: Int? = null,
    @SerializedName("today_minutes") val todayMinutes: Int? = null,
    @SerializedName("today_attempts") val todayAttempts: Int? = null,
    @SerializedName("streak_current_days") val streakCurrentDays: Int? = null,
    @SerializedName("unread_notifications") val unreadNotifications: Int? = null,
    @SerializedName("smart_guide_completed_steps") val smartGuideCompletedSteps: Int? = null,
    @SerializedName("smart_guide_total_steps") val smartGuideTotalSteps: Int? = null,
    @SerializedName("smart_guide_completion_percent") val smartGuideCompletionPercent: Int? = null,
)

data class QuestionConfirmedSubjectDeltaDto(
    @SerializedName("subject_version_id") val subjectVersionId: Int = 0,
    val points: Int? = null,
    val level: Int? = null,
    @SerializedName("level_progress_percent") val levelProgressPercent: Int? = null,
    val hearts: Int? = null,
)

data class QuestionConfirmedDeltaDto(
    val available: Boolean = false,
    val reason: String = "",
    @SerializedName("student_id") val studentId: String? = null,
    val profile: QuestionConfirmedProfileDeltaDto? = null,
    val subjects: List<QuestionConfirmedSubjectDeltaDto> = emptyList(),
    @SerializedName("server_version") val serverVersion: String? = null,
    @SerializedName("confirmed_at_epoch_millis") val confirmedAtEpochMillis: Long? = null,
)

data class QuestionFinishResultDto(
    @SerializedName("session_id") val sessionId: String = "",
    val status: String = "",
    @SerializedName("completed_at") val completedAt: String = "",
    val replayed: Boolean = false,
    val result: QuestionSessionScoreDto = QuestionSessionScoreDto(),
    val policy: QuestionSessionPolicyDto = QuestionSessionPolicyDto(),
    @SerializedName("confirmed_delta") val confirmedDelta: QuestionConfirmedDeltaDto =
        QuestionConfirmedDeltaDto(),
)

data class QuestionFinishResponseDto(
    val success: Boolean = false,
    val data: QuestionFinishResultDto? = null,
    val error: QuestionSessionApiErrorDto? = null,
    @SerializedName("request_id") val requestId: String = "",
)

interface StudentQuestionSessionApi {
    @GET("/api/v1/student/activity/session")
    suspend fun sessionPackage(
        @Header("Authorization") authorization: String,
        @Query("session_id") sessionId: String,
    ): QuestionSessionPackageResponseDto

    @POST("/api/v1/student/activity/answer")
    suspend fun submitAnswer(
        @Header("Authorization") authorization: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: QuestionAnswerRequestDto,
    ): QuestionAnswerResponseDto

    @POST("/api/v1/student/activity/finish")
    suspend fun finishSession(
        @Header("Authorization") authorization: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: QuestionFinishRequestDto,
    ): QuestionFinishResponseDto
}
