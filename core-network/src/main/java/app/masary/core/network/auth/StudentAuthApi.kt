package app.masary.core.network.auth

import retrofit2.http.Body
import retrofit2.http.POST

class StudentLoginRequestDto(val studentId: String, val password: String) {
    override fun toString(): String = "StudentLoginRequestDto(studentId=$studentId, password=[REDACTED])"
}
data class StudentDto(val id: String, val name: String)
data class StudentLoginResponseDto(val accessToken: String, val student: StudentDto)

interface StudentAuthApi {
    @POST("/api/v1/auth/student/login")
    suspend fun login(@Body request: StudentLoginRequestDto): StudentLoginResponseDto
}
