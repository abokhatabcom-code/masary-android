package app.masary.core.network.training

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

data class StudentTrainingCenterResponseDto(
    val success: Boolean,
    val data: StudentTrainingCenterDataDto? = null,
    val error: StudentTrainingCenterErrorDto? = null,
)

data class StudentTrainingCenterErrorDto(
    val code: String = "",
    val message: String = "",
)

data class StudentTrainingCenterDataDto(
    @SerializedName("student_id") val studentId: String = "",
    @SerializedName("subject_version_id") val subjectVersionId: Int = 0,
    @SerializedName("generated_at") val generatedAt: String = "",
    val identity: TrainingCenterIdentityDto = TrainingCenterIdentityDto(),
    val tools: List<TrainingCenterToolDto> = emptyList(),
    val version: String = "",
)

data class TrainingCenterIdentityDto(
    val name: String = "",
    @SerializedName("curriculum_label") val curriculumLabel: String = "",
)

data class TrainingCenterToolDto(
    val key: String = "",
    val title: String = "",
    val description: String = "",
    @SerializedName("activity_type") val activityType: String = "",
    @SerializedName("activity_mode") val activityMode: String = "",
    val source: String = "",
    val available: Boolean = false,
    val status: String = "source_unavailable",
    val reason: String = "",
    @SerializedName("item_count") val itemCount: Int? = null,
)

interface StudentTrainingCenterApi {
    @GET("/api/v1/student/subject/training-center")
    suspend fun trainingCenter(
        @Header("Authorization") authorization: String,
        @Query("subject_version_id") subjectVersionId: Int,
    ): StudentTrainingCenterResponseDto
}
