package app.masary.feature.auth.data

import app.masary.core.models.auth.AuthenticatedStudent
import app.masary.core.models.auth.AuthTokens
import app.masary.core.models.auth.Student
import app.masary.feature.auth.domain.AuthRepository

object AuthRepositoryFactory {
    const val DEMO_USERNAME = "student-demo"
    const val DEMO_PASSWORD = "Masary123"

    fun create(): AuthRepository = FakeAuthRepository()
}

class FakeAuthRepository : AuthRepository {
    override suspend fun login(username: String, password: CharArray, deviceName: String): Result<AuthenticatedStudent> = try {
        if (username == AuthRepositoryFactory.DEMO_USERNAME &&
            password.contentEquals(AuthRepositoryFactory.DEMO_PASSWORD.toCharArray())
        ) {
            Result.success(
                AuthenticatedStudent(
                    Student("debug-student", username, "الطالب التجريبي"),
                    AuthTokens("debug-access", "debug-refresh", 3_600),
                ),
            )
        } else {
            Result.failure(IllegalArgumentException("بيانات الدخول غير صحيحة"))
        }
    } finally {
        password.fill('\u0000')
    }
}
