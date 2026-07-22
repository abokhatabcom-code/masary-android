package app.masary.feature.auth

import app.masary.core.models.auth.AuthTokens
import app.masary.core.network.auth.*
import app.masary.core.network.auth.StudentDto
import app.masary.core.network.auth.StudentLoginDataDto
import app.masary.core.network.auth.StudentLoginRequestDto
import app.masary.core.network.auth.StudentLoginResponseDto
import app.masary.core.network.auth.StudentLogoutDataDto
import app.masary.core.network.auth.StudentLogoutRequestDto
import app.masary.core.network.auth.StudentLogoutResponseDto
import app.masary.core.network.auth.StudentMeDataDto
import app.masary.core.network.auth.StudentMeResponseDto
import app.masary.core.network.auth.StudentRefreshDataDto
import app.masary.core.network.auth.StudentRefreshRequestDto
import app.masary.core.network.auth.StudentRefreshResponseDto
import app.masary.feature.auth.data.NetworkAuthRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkAuthRepositoryTest {
    @Test fun `login uses real API contract and wipes password`() = runTest {
        val api = RecordingStudentAuthApi()
        val repository = NetworkAuthRepository(api) { 1_000L }
        val password = "private".toCharArray()

        val result = repository.login("student", password, "test-device")

        assertTrue(result.isSuccess)
        assertEquals("student", api.loginRequest?.username)
        assertEquals("test-device", api.loginRequest?.deviceName)
        assertTrue(password.all { it == '\u0000' })
        assertEquals(1_900L, result.getOrThrow().tokens.accessTokenExpiresAtEpochSeconds)
    }

    @Test fun `refresh rotates both tokens`() = runTest {
        val api = RecordingStudentAuthApi()
        val repository = NetworkAuthRepository(api) { 2_000L }

        val tokens = repository.refresh("old-refresh").getOrThrow()

        assertEquals("old-refresh", api.refreshRequest?.refreshToken)
        assertEquals("new-access", tokens.accessToken)
        assertEquals("new-refresh", tokens.refreshToken)
        assertEquals(2_900L, tokens.accessTokenExpiresAtEpochSeconds)
    }

    @Test fun `logout sends bearer token and refresh token`() = runTest {
        val api = RecordingStudentAuthApi()
        val repository = NetworkAuthRepository(api) { 1_000L }
        val tokens = AuthTokens("access", "refresh", 900, 2_000L)

        assertTrue(repository.logout(tokens).isSuccess)
        assertEquals("Bearer access", api.logoutAuthorization)
        assertEquals("refresh", api.logoutRequest?.refreshToken)
        assertFalse(api.logoutAuthorization.orEmpty().contains("refresh"))
    }
}

private class RecordingStudentAuthApi : StudentAuthApi {
    override suspend fun cities() = CitiesResponseDto(true, CitiesDataDto(emptyList()))
    override suspend fun grades() = GradesResponseDto(true, GradesDataDto(emptyList()))
    override suspend fun schools(cityId: Long) = SchoolsResponseDto(true, SchoolsDataDto(emptyList()))
    override suspend fun register(idempotencyKey: String, request: StudentRegistrationRequestDto) = StudentLoginResponseDto(false)

    var loginRequest: StudentLoginRequestDto? = null
    var refreshRequest: StudentRefreshRequestDto? = null
    var logoutRequest: StudentLogoutRequestDto? = null
    var logoutAuthorization: String? = null

    override suspend fun login(request: StudentLoginRequestDto): StudentLoginResponseDto {
        loginRequest = request
        return StudentLoginResponseDto(
            success = true,
            data = StudentLoginDataDto(
                accessToken = "access",
                refreshToken = "refresh",
                expiresIn = 900,
                student = StudentDto("7", "student", "سارة"),
            ),
        )
    }

    override suspend fun refresh(request: StudentRefreshRequestDto): StudentRefreshResponseDto {
        refreshRequest = request
        return StudentRefreshResponseDto(
            success = true,
            data = StudentRefreshDataDto(
                accessToken = "new-access",
                refreshToken = "new-refresh",
                expiresIn = 900,
                student = StudentDto("7", "student", "سارة"),
            ),
        )
    }

    override suspend fun logout(
        authorization: String,
        request: StudentLogoutRequestDto,
    ): StudentLogoutResponseDto {
        logoutAuthorization = authorization
        logoutRequest = request
        return StudentLogoutResponseDto(true, StudentLogoutDataDto(true))
    }

    override suspend fun me(authorization: String): StudentMeResponseDto =
        StudentMeResponseDto(true, StudentMeDataDto(StudentDto("7", "student", "سارة")))
}
