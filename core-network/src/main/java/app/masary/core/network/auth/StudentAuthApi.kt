package app.masary.core.network.auth

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.POST

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

data class StudentDto(
    val id: String,
    val username: String,
    @SerializedName("display_name") val displayName: String,
)

data class StudentLoginErrorDto(val code: String, val message: String)

interface StudentAuthApi {
    @POST("/api/v1/auth/student/login")
    suspend fun login(@Body request: StudentLoginRequestDto): StudentLoginResponseDto
}
