package app.masary.feature.auth.data

import app.masary.core.datastore.StudentSession
import app.masary.feature.auth.domain.AuthRepository

object AuthRepositoryFactory {
    const val DEMO_STUDENT_ID = "student-demo"
    const val DEMO_PASSWORD = "Masary123"

    fun create(): AuthRepository = FakeAuthRepository()
}

class FakeAuthRepository : AuthRepository {
    override suspend fun login(studentId: String, password: CharArray): Result<StudentSession> = try {
        if (studentId == AuthRepositoryFactory.DEMO_STUDENT_ID &&
            password.contentEquals(AuthRepositoryFactory.DEMO_PASSWORD.toCharArray())
        ) {
            Result.success(StudentSession(studentId, "الطالب التجريبي", "debug-session"))
        } else {
            Result.failure(IllegalArgumentException("بيانات الدخول غير صحيحة"))
        }
    } finally {
        password.fill('\u0000')
    }
}
