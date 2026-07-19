package app.masary.core.models.auth

data class Student(
    val id: String,
    val username: String,
    val displayName: String,
)

data class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresInSeconds: Long,
)

data class AuthenticatedStudent(
    val student: Student,
    val tokens: AuthTokens,
)

data class StudentSession(
    val id: String,
    val username: String,
    val displayName: String,
)
