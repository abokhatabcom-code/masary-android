package app.masary.feature.auth.data

import app.masary.core.models.auth.AuthenticatedStudent
import app.masary.core.models.auth.AuthTokens
import app.masary.core.models.auth.Student
import app.masary.core.network.auth.StudentAuthApi
import app.masary.core.network.auth.StudentLoginRequestDto
import app.masary.core.network.auth.StudentLogoutRequestDto
import app.masary.core.network.auth.StudentRefreshRequestDto
import app.masary.feature.auth.domain.AuthFailureException
import app.masary.feature.auth.domain.AuthFailureKind
import app.masary.feature.auth.domain.AuthRepository
import java.io.IOException
import retrofit2.HttpException

class NetworkAuthRepository(
    private val api: StudentAuthApi,
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1_000L },
) : AuthRepository {
    override suspend fun login(
        username: String,
        password: CharArray,
        deviceName: String,
    ): Result<AuthenticatedStudent> = try {
        runCatching {
            val response = api.login(
                StudentLoginRequestDto(username, password.concatToString(), deviceName),
            )
            val data = response.data
            if (!response.success || data == null) {
                throw AuthFailureException(
                    AuthFailureKind.INVALID_CREDENTIALS,
                    response.error?.message ?: "بيانات الدخول غير صحيحة أو الحساب غير متاح حاليًا.",
                )
            }
            AuthenticatedStudent(
                student = Student(data.student.id, data.student.username, data.student.displayName),
                tokens = tokens(data.accessToken, data.refreshToken, data.expiresIn),
            )
        }.recoverCatching { throw mapFailure(it, AuthFailureKind.INVALID_CREDENTIALS) }
    } finally {
        password.fill('\u0000')
    }

    override suspend fun refresh(refreshToken: String): Result<AuthTokens> = runCatching {
        val response = api.refresh(StudentRefreshRequestDto(refreshToken))
        val data = response.data
        if (!response.success || data == null) {
            throw AuthFailureException(
                AuthFailureKind.SESSION_REJECTED,
                response.error?.message ?: "انتهت جلسة الدخول. سجّل الدخول من جديد.",
            )
        }
        tokens(data.accessToken, data.refreshToken, data.expiresIn)
    }.recoverCatching { throw mapFailure(it, AuthFailureKind.SESSION_REJECTED) }

    override suspend fun logout(tokens: AuthTokens): Result<Unit> = runCatching {
        var activeTokens = tokens
        if (activeTokens.accessTokenNeedsRefresh()) {
            refresh(activeTokens.refreshToken).getOrNull()?.let { activeTokens = it }
        }

        val request = StudentLogoutRequestDto(activeTokens.refreshToken)
        val response = try {
            api.logout("Bearer ${activeTokens.accessToken}", request)
        } catch (error: HttpException) {
            if (error.code() != 401) throw error
            // The server also supports revocation by refresh token when the access token expired.
            api.logout("", request)
        }
        if (!response.success) {
            throw AuthFailureException(
                AuthFailureKind.SERVER,
                response.error?.message ?: "تعذر إنهاء الجلسة على الخادم.",
            )
        }
    }.recoverCatching { throw mapFailure(it, AuthFailureKind.SERVER) }

    private fun tokens(accessToken: String, refreshToken: String, expiresIn: Long): AuthTokens =
        AuthTokens(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresInSeconds = expiresIn,
            accessTokenExpiresAtEpochSeconds = nowEpochSeconds() + expiresIn,
        )

    private fun mapFailure(error: Throwable, unauthorizedKind: AuthFailureKind): Throwable = when (error) {
        is AuthFailureException -> error
        is IOException -> AuthFailureException(
            AuthFailureKind.NETWORK,
            "تعذر الاتصال بالمنصة. تحقق من الإنترنت وحاول مرة أخرى.",
            error,
        )
        is HttpException -> when (error.code()) {
            400, 401, 403 -> AuthFailureException(
                unauthorizedKind,
                if (unauthorizedKind == AuthFailureKind.INVALID_CREDENTIALS) {
                    "بيانات الدخول غير صحيحة أو الحساب غير متاح حاليًا."
                } else {
                    "انتهت جلسة الدخول. سجّل الدخول من جديد."
                },
                error,
            )
            429 -> AuthFailureException(
                AuthFailureKind.SERVER,
                "محاولات كثيرة خلال وقت قصير. انتظر قليلًا ثم حاول مرة أخرى.",
                error,
            )
            else -> AuthFailureException(
                AuthFailureKind.SERVER,
                "الخدمة غير متاحة مؤقتًا. حاول مرة أخرى.",
                error,
            )
        }
        else -> AuthFailureException(
            AuthFailureKind.SERVER,
            "حدث خطأ غير متوقع. حاول مرة أخرى.",
            error,
        )
    }
}
