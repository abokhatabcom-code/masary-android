package app.masary.feature.auth.data

import app.masary.core.datastore.StudentSession
import app.masary.core.network.auth.StudentAuthApi
import app.masary.core.network.auth.StudentLoginRequestDto
import app.masary.feature.auth.domain.AuthRepository

class NetworkAuthRepository(private val api: StudentAuthApi) : AuthRepository {
    override suspend fun login(studentId: String, password: CharArray): Result<StudentSession> =
        runCatching {
            val response = api.login(StudentLoginRequestDto(studentId, password.concatToString()))
            StudentSession(response.student.id, response.student.name, response.accessToken)
        }.also { password.fill('\u0000') }
}
