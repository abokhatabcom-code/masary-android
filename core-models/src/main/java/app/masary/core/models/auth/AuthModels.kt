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
    val accessTokenExpiresAtEpochSeconds: Long =
        (System.currentTimeMillis() / 1_000L) + expiresInSeconds,
) {
    fun accessTokenNeedsRefresh(
        nowEpochSeconds: Long = System.currentTimeMillis() / 1_000L,
        refreshBeforeSeconds: Long = 60L,
    ): Boolean = accessTokenExpiresAtEpochSeconds <= nowEpochSeconds + refreshBeforeSeconds
}

data class AuthenticatedStudent(
    val student: Student,
    val tokens: AuthTokens,
)

data class StudentSession(
    val id: String,
    val username: String,
    val displayName: String,
)
