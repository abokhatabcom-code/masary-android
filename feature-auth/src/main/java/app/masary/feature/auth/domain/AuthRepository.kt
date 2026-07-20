package app.masary.feature.auth.domain

import app.masary.core.models.auth.AuthenticatedStudent
import app.masary.core.models.auth.AuthTokens

enum class AuthFailureKind {
    INVALID_CREDENTIALS,
    SESSION_REJECTED,
    NETWORK,
    SERVER,
}

class AuthFailureException(
    val kind: AuthFailureKind,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

fun interface AuthRepository {
    suspend fun login(username: String, password: CharArray, deviceName: String): Result<AuthenticatedStudent>

    suspend fun refresh(refreshToken: String): Result<AuthTokens> =
        Result.failure(AuthFailureException(AuthFailureKind.SESSION_REJECTED, "انتهت جلسة الدخول"))

    suspend fun validateSession(tokens: AuthTokens): Result<AuthTokens> =
        if (tokens.accessTokenNeedsRefresh()) refresh(tokens.refreshToken) else Result.success(tokens)

    suspend fun logout(tokens: AuthTokens): Result<Unit> = Result.success(Unit)
}
