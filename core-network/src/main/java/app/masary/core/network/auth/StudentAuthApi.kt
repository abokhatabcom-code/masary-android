package app.masary.core.network.auth

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

data class StudentLoginRequestDto(
    val username: String,
    val password: String,
    @SerializedName("device_name") val deviceName: String,
) {
    override fun toString(): String =
        "StudentLoginRequestDto(username=$username, password=[REDACTED], deviceName=$deviceName)"
}

data class StudentLoginResponseDto(
    val success: Boolean,
    val data: StudentLoginDataDto? = null,
    val error: StudentLoginErrorDto? = null,
)

data class StudentLoginDataDto(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String,
    @SerializedName("expires_in") val expiresIn: Long,
    val student: StudentDto,
)

data class StudentRefreshRequestDto(
    @SerializedName("refresh_token") val refreshToken: String,
) {
    override fun toString(): String = "StudentRefreshRequestDto(refreshToken=[REDACTED])"
}

data class StudentRefreshResponseDto(
    val success: Boolean,
    val data: StudentRefreshDataDto? = null,
    val error: StudentLoginErrorDto? = null,
)

data class StudentRefreshDataDto(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String,
    @SerializedName("expires_in") val expiresIn: Long,
    val student: StudentDto? = null,
)

data class StudentLogoutRequestDto(
    @SerializedName("refresh_token") val refreshToken: String,
) {
    override fun toString(): String = "StudentLogoutRequestDto(refreshToken=[REDACTED])"
}

data class StudentLogoutResponseDto(
    val success: Boolean,
    val data: StudentLogoutDataDto? = null,
    val error: StudentLoginErrorDto? = null,
)

data class StudentLogoutDataDto(
    @SerializedName("logged_out") val loggedOut: Boolean,
)

data class StudentMeResponseDto(
    val success: Boolean,
    val data: StudentMeDataDto? = null,
    val error: StudentLoginErrorDto? = null,
)

data class StudentMeDataDto(val student: StudentDto)

data class StudentDto(
    val id: String,
    val username: String,
    @SerializedName("display_name") val displayName: String,
)

data class StudentLoginErrorDto(val code: String, val message: String)

interface StudentAuthApi {
    @GET("/api/v1/registration/cities") suspend fun cities(): CitiesResponseDto
    @GET("/api/v1/registration/grades") suspend fun grades(): GradesResponseDto
    @GET("/api/v1/registration/schools") suspend fun schools(@Query("city_id") cityId: Long): SchoolsResponseDto
    @POST("/api/v1/auth/student/register") suspend fun register(@Header("Idempotency-Key") idempotencyKey: String, @Body request: StudentRegistrationRequestDto): StudentLoginResponseDto

    @POST("/api/v1/auth/student/login")
    suspend fun login(@Body request: StudentLoginRequestDto): StudentLoginResponseDto

    @POST("/api/v1/auth/refresh")
    suspend fun refresh(@Body request: StudentRefreshRequestDto): StudentRefreshResponseDto

    @POST("/api/v1/auth/logout")
    suspend fun logout(
        @Header("Authorization") authorization: String,
        @Body request: StudentLogoutRequestDto,
    ): StudentLogoutResponseDto

    @GET("/api/v1/me")
    suspend fun me(@Header("Authorization") authorization: String): StudentMeResponseDto
}

data class AcademicOptionDto(val id: Long, val name: String)
data class CityDto(val id: Long, val name: String, @SerializedName("requires_school") val requiresSchool: Boolean)
data class CitiesDataDto(val cities: List<CityDto>)
data class GradesDataDto(val grades: List<AcademicOptionDto>)
data class SchoolsDataDto(val schools: List<AcademicOptionDto>)
data class CitiesResponseDto(val success: Boolean, val data: CitiesDataDto? = null, val error: StudentLoginErrorDto? = null)
data class GradesResponseDto(val success: Boolean, val data: GradesDataDto? = null, val error: StudentLoginErrorDto? = null)
data class SchoolsResponseDto(val success: Boolean, val data: SchoolsDataDto? = null, val error: StudentLoginErrorDto? = null)
data class StudentRegistrationRequestDto(
    @SerializedName("full_name") val fullName: String, val username: String, val phone: String?, val email: String?,
    val password: String, @SerializedName("password_confirmation") val passwordConfirmation: String,
    val gender: String, @SerializedName("student_personality") val studentPersonality: String?,
    @SerializedName("city_id") val cityId: Long, @SerializedName("school_id") val schoolId: Long?,
    @SerializedName("grade_id") val gradeId: Long, @SerializedName("privacy_accept") val privacyAccept: Boolean,
    @SerializedName("device_name") val deviceName: String,
) { override fun toString() = "StudentRegistrationRequestDto(username=$username, password=[REDACTED])" }
