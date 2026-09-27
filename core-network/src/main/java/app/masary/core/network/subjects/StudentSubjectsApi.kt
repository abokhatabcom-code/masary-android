package app.masary.core.network.subjects

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Header

data class StudentSubjectsResponseDto(
    val success: Boolean,
    val data: StudentSubjectsDataDto? = null,
    val error: StudentSubjectsErrorDto? = null,
)

data class StudentSubjectsErrorDto(
    val code: String = "",
    val message: String = "",
)

data class StudentSubjectsDataDto(
    @SerializedName("student_id") val studentId: String = "",
    val version: String = "",
    @SerializedName("generated_at") val generatedAt: String = "",
    val complete: Boolean = true,
    val academic: SubjectsAcademicContextDto = SubjectsAcademicContextDto(),
    val subjects: List<StudentSubjectSummaryDto> = emptyList(),
    val empty: SubjectsEmptyStateDto = SubjectsEmptyStateDto(),
)

data class SubjectsAcademicContextDto(
    val available: Boolean = false,
    @SerializedName("grade_name") val gradeName: String = "",
    @SerializedName("department_name") val departmentName: String = "",
    @SerializedName("city_name") val cityName: String = "",
    @SerializedName("curriculum_name") val curriculumName: String = "",
    val reason: String = "",
)

data class StudentSubjectSummaryDto(
    @SerializedName("subject_version_id") val subjectVersionId: Int = 0,
    val name: String = "",
    val hearts: Int = 0,
    val points: Int? = null,
    val level: Int? = null,
    @SerializedName("curriculum_label") val curriculumLabel: String = "",
    val progress: SubjectProgressDto = SubjectProgressDto(),
    val media: SubjectMediaDto = SubjectMediaDto(),
    val access: SubjectAccessDto = SubjectAccessDto(),
    @SerializedName("last_activity") val lastActivity: SubjectLastActivityDto = SubjectLastActivityDto(),
)

data class SubjectProgressDto(
    val available: Boolean = false,
    val percent: Int? = null,
    val reason: String = "",
)

data class SubjectMediaDto(
    val available: Boolean = false,
    val key: String? = null,
    val reason: String = "",
)

data class SubjectAccessDto(
    val available: Boolean = false,
    val status: String = "unknown",
    val reason: String = "",
)

data class SubjectLastActivityDto(
    val available: Boolean = false,
    @SerializedName("unit_id") val unitId: Int? = null,
    val mode: String = "",
    @SerializedName("updated_at") val updatedAt: String = "",
    val reason: String = "",
)

data class SubjectsEmptyStateDto(
    @SerializedName("is_empty") val isEmpty: Boolean = false,
    val reason: String = "",
)

interface StudentSubjectsApi {
    @GET("/api/v1/student/subjects")
    suspend fun subjects(
        @Header("Authorization") authorization: String,
    ): StudentSubjectsResponseDto
}
