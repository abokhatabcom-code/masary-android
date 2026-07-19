package app.masary.feature.auth.data

import app.masary.core.models.auth.AuthenticatedStudent
import app.masary.core.models.auth.AuthTokens
import app.masary.core.models.auth.Student
import app.masary.core.network.auth.StudentAuthApi
import app.masary.core.network.auth.StudentLoginRequestDto
import app.masary.feature.auth.domain.AuthRepository

class NetworkAuthRepository(private val api: StudentAuthApi) : AuthRepository {
    override suspend fun login(username: String, password: CharArray, deviceName: String): Result<AuthenticatedStudent> =
        try {
            runCatching {
                val response = api.login(StudentLoginRequestDto(username, password.concatToString(), deviceName))
                val data = response.data
                if (!response.success || data == null) {
                    error(response.error?.message ?: "تعذر تسجيل الدخول")
                }
                AuthenticatedStudent(
                    student = Student(data.student.id, data.student.username, data.student.displayName),
                    tokens = AuthTokens(data.accessToken, data.refreshToken, data.expiresIn),
                )
            }
        } finally {
            password.fill('\u0000')
        }
}
