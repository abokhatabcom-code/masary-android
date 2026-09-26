package app.masary.core.network.subject

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

data class StudentSubjectDetailResponseDto(
    val success: Boolean,
    val data: StudentSubjectDetailDataDto? = null,
    val error: StudentSubjectDetailErrorDto? = null,
)

data class StudentSubjectDetailErrorDto(
    val code: String = "",
    val message: String = "",
)

data class StudentSubjectDetailDataDto(
    @SerializedName("student_id") val studentId: String = "",
    @SerializedName("subject_version_id") val subjectVersionId: Int = 0,
    @SerializedName("generated_at") val generatedAt: String = "",
    val identity: SubjectDetailIdentityDto = SubjectDetailIdentityDto(),
    val points: SubjectAvailabilityIntDto = SubjectAvailabilityIntDto(),
    val level: SubjectAvailabilityIntDto = SubjectAvailabilityIntDto(),
    val progress: SubjectDetailProgressDto = SubjectDetailProgressDto(),
    val hearts: SubjectDetailHeartsDto = SubjectDetailHeartsDto(),
    val access: SubjectDetailAccessDto = SubjectDetailAccessDto(),
    val content: SubjectDetailContentDto = SubjectDetailContentDto(),
    @SerializedName("last_activity") val lastActivity: SubjectDetailLastActivityDto = SubjectDetailLastActivityDto(),
    val actions: SubjectDetailActionsDto = SubjectDetailActionsDto(),
    val version: String = "",
)

data class SubjectDetailIdentityDto(
    val name: String = "",
    @SerializedName("curriculum_label") val curriculumLabel: String = "",
    @SerializedName("version_type") val versionType: String = "",
    val media: SubjectDetailMediaDto = SubjectDetailMediaDto(),
)

data class SubjectDetailMediaDto(
    val available: Boolean = false,
    val key: String? = null,
    val reason: String = "",
)

data class SubjectAvailabilityIntDto(
    val available: Boolean = false,
    val value: Int? = null,
    val reason: String = "",
)

data class SubjectDetailProgressDto(
    val available: Boolean = false,
    val percent: Int? = null,
    val reason: String = "",
)

data class SubjectDetailRestoreDto(
    val available: Boolean = false,
    val at: String? = null,
    val reason: String = "",
)

data class SubjectDetailHeartsDto(
    val current: Int = 0,
    val maximum: Int = 3,
    @SerializedName("next_restore") val nextRestore: SubjectDetailRestoreDto = SubjectDetailRestoreDto(),
)

data class SubjectDetailAccessDto(
    val available: Boolean = false,
    val status: String = "unknown",
    val reason: String = "",
)

data class SubjectContentPartDto(
    @SerializedName("part_number") val partNumber: Int = 0,
    val label: String = "",
    @SerializedName("units_count") val unitsCount: Int = 0,
    @SerializedName("lessons_count") val lessonsCount: Int = 0,
)

data class SubjectLearningStateDto(
    val status: String = "unknown",
    val reason: String = "",
)

data class SubjectLessonDto(
    val id: Int = 0,
    @SerializedName("unit_id") val unitId: Int? = null,
    @SerializedName("part_number") val partNumber: Int = 0,
    val title: String = "",
    val position: Int = 0,
    val state: SubjectLearningStateDto = SubjectLearningStateDto(),
    val preparation: SubjectActionAvailabilityDto = SubjectActionAvailabilityDto(),
)

data class SubjectUnitDto(
    val id: Int = 0,
    @SerializedName("part_number") val partNumber: Int = 0,
    val title: String = "",
    val position: Int = 0,
    val state: SubjectLearningStateDto = SubjectLearningStateDto(),
    val lessons: List<SubjectLessonDto> = emptyList(),
)

data class SubjectDetailContentDto(
    @SerializedName("structure_mode") val structureMode: String = "unknown",
    @SerializedName("has_parts") val hasParts: Boolean = false,
    val parts: List<SubjectContentPartDto> = emptyList(),
    @SerializedName("details_available") val detailsAvailable: Boolean = false,
    val units: List<SubjectUnitDto> = emptyList(),
    val lessons: List<SubjectLessonDto> = emptyList(),
    val reason: String = "",
)

data class SubjectActionAvailabilityDto(
    val available: Boolean = false,
    val reason: String = "",
)

data class SubjectDetailLastActivityDto(
    val available: Boolean = false,
    @SerializedName("unit_id") val unitId: Int? = null,
    @SerializedName("lesson_id") val lessonId: Int? = null,
    val mode: String = "",
    @SerializedName("updated_at") val updatedAt: String = "",
    val preparation: SubjectActionAvailabilityDto = SubjectActionAvailabilityDto(),
    val reason: String = "",
)

data class SubjectDetailActionsDto(
    @SerializedName("training_center") val trainingCenter: SubjectActionAvailabilityDto =
        SubjectActionAvailabilityDto(),
)

interface StudentSubjectApi {
    @GET("/api/v1/student/subject")
    suspend fun subject(
        @Header("Authorization") authorization: String,
        @Query("subject_version_id") subjectVersionId: Int,
    ): StudentSubjectDetailResponseDto
}
