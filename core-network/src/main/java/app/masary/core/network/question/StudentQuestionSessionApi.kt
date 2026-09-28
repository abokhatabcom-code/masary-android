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

data class QuestionSessionPackageDataDto(
    val version: String = "",
    @SerializedName("generated_at") val generatedAt: String = "",
    val session: QuestionSessionDescriptorDto = QuestionSessionDescriptorDto(),
    val progress: QuestionSessionProgressDto = QuestionSessionProgressDto(),
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
}
