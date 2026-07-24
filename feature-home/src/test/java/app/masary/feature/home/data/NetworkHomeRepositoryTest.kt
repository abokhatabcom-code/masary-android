package app.masary.feature.home.data

import app.masary.core.datastore.SessionManager
import app.masary.core.models.auth.AuthenticatedStudent
import app.masary.core.models.auth.AuthTokens
import app.masary.core.models.auth.StudentSession
import app.masary.core.network.auth.*
import app.masary.core.network.auth.StudentLoginRequestDto
import app.masary.core.network.auth.StudentLoginResponseDto
import app.masary.core.network.auth.StudentLogoutDataDto
import app.masary.core.network.auth.StudentLogoutRequestDto
import app.masary.core.network.auth.StudentLogoutResponseDto
import app.masary.core.network.auth.StudentMeResponseDto
import app.masary.core.network.auth.StudentRefreshDataDto
import app.masary.core.network.auth.StudentRefreshRequestDto
import app.masary.core.network.auth.StudentRefreshResponseDto
import app.masary.core.network.home.HomeSmartGuideDto
import app.masary.core.network.home.HomeSmartGuideStepDto
import app.masary.core.network.home.HomeStudentDto
import app.masary.core.network.home.StudentHomeApi
import app.masary.core.network.home.StudentHomeDataDto
import app.masary.core.network.home.StudentHomeResponseDto
import app.masary.feature.home.domain.HomeSnapshotStore
import app.masary.feature.home.domain.HomeSnapshotMetadata
import app.masary.feature.home.domain.StudentHomeData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import kotlinx.coroutines.CancellationException

class NetworkHomeRepositoryTest {
    @Test
    fun `refreshes an expired token and preserves guide order`() = runTest {
        val sessionManager = FakeSessionManager(
            AuthTokens(
                accessToken = "expired-access",
                refreshToken = "refresh-1",
                expiresInSeconds = 1,
                accessTokenExpiresAtEpochSeconds = 1,
            ),
        )
        val authApi = FakeAuthApi()
        val homeApi = FakeHomeApi()
        val repository = NetworkHomeRepository(
            homeApi = homeApi,
            authApi = authApi,
            sessionManager = sessionManager,
            snapshotStore = FakeSnapshotStore(),
            nowEpochSeconds = { 1000L },
        )

        val result = repository.loadHome()

        assertTrue(result.isSuccess)
        assertEquals("new-access", sessionManager.tokens?.accessToken)
        assertEquals("Bearer new-access", homeApi.lastAuthorization)
        assertEquals(listOf(1, 2), result.getOrThrow().smartGuide.steps.map { it.sortOrder })
    }

    @Test
    fun `exposes the snapshot before a background refresh fails`() = runTest {
        val sessionManager = FakeSessionManager(AuthTokens("access", "refresh", 900, 2_000))
        val snapshot = FakeSnapshotStore()
        val homeApi = FakeHomeApi()
        val repository = NetworkHomeRepository(homeApi, FakeAuthApi(), sessionManager, snapshot) { 1_000 }
        val online = repository.loadHome().getOrThrow()
        homeApi.offline = true

        val cached = repository.loadSnapshot()
        val refresh = repository.loadHome()

        assertEquals(online.student.id, cached?.student?.id)
        assertTrue(cached?.snapshot != null)
        assertTrue(refresh.isFailure)
    }

    @Test
    fun `does not hide a platform business error with a snapshot`() = runTest {
        val sessionManager = FakeSessionManager(AuthTokens("access", "refresh", 900, 2_000))
        val snapshot = FakeSnapshotStore()
        val homeApi = FakeHomeApi()
        val repository = NetworkHomeRepository(homeApi, FakeAuthApi(), sessionManager, snapshot) { 1_000 }
        repository.loadHome().getOrThrow()
        homeApi.serviceError = true
        val result = repository.loadHome()

        assertTrue(result.isFailure)
    }

    @Test
    fun `cache write failure does not discard a successful platform response`() = runTest {
        val session = FakeSessionManager(AuthTokens("access", "refresh", 900, 2_000))
        val snapshot = FakeSnapshotStore().apply { failWrites = true }
        val result = NetworkHomeRepository(FakeHomeApi(), FakeAuthApi(), session, snapshot) { 1_000 }.loadHome()
        assertTrue(result.isSuccess)
    }

    @Test(expected = CancellationException::class)
    fun `cancellation is never mapped to a service error`() = runTest {
        val session = FakeSessionManager(AuthTokens("access", "refresh", 900, 2_000))
        NetworkHomeRepository(FakeHomeApi().apply { cancelled = true }, FakeAuthApi(), session, FakeSnapshotStore()) { 1_000 }
            .loadHome().getOrThrow()
    }

    @Test
    fun `rejects a home payload belonging to another student`() = runTest {
        val session = FakeSessionManager(AuthTokens("access", "refresh", 900, 2_000))
        val result = NetworkHomeRepository(
            FakeHomeApi().apply { responseStudentId = "99" },
            FakeAuthApi(),
            session,
            FakeSnapshotStore(),
        ) { 1_000 }.loadHome()
        assertTrue(result.exceptionOrNull() is app.masary.feature.home.domain.HomeSessionExpiredException)
    }
}

private class FakeSessionManager(
    var tokens: AuthTokens?,
) : SessionManager {
    override val session: Flow<StudentSession?> = flowOf(StudentSession("42", "student", "طالب"))

    override suspend fun save(authenticatedStudent: AuthenticatedStudent) = Unit

    override suspend fun readTokens(): AuthTokens? = tokens

    override suspend fun updateTokens(tokens: AuthTokens) {
        this.tokens = tokens
    }

    override suspend fun clear() {
        tokens = null
    }
}

private class FakeSnapshotStore : HomeSnapshotStore {
    var value: StudentHomeData? = null
    var failWrites = false
    override suspend fun read(studentId: String) = value?.copy(snapshot = HomeSnapshotMetadata(1L))
    override suspend fun write(studentId: String, data: StudentHomeData) {
        if (failWrites) throw IOException("disk full")
        value = data
    }
    override suspend fun clear() { value = null }
}

private class FakeAuthApi : StudentAuthApi {
    override suspend fun cities() = CitiesResponseDto(true, CitiesDataDto(emptyList()))
    override suspend fun grades() = GradesResponseDto(true, GradesDataDto(emptyList()))
    override suspend fun schools(cityId: Long) = SchoolsResponseDto(true, SchoolsDataDto(emptyList()))
    override suspend fun register(idempotencyKey: String, request: StudentRegistrationRequestDto) = error("Not used")

    override suspend fun login(request: StudentLoginRequestDto): StudentLoginResponseDto =
        error("Not used")

    override suspend fun refresh(request: StudentRefreshRequestDto): StudentRefreshResponseDto =
        StudentRefreshResponseDto(
            success = true,
            data = StudentRefreshDataDto(
                accessToken = "new-access",
                refreshToken = "refresh-2",
                expiresIn = 900,
            ),
        )

    override suspend fun logout(
        authorization: String,
        request: StudentLogoutRequestDto,
    ): StudentLogoutResponseDto = StudentLogoutResponseDto(
        success = true,
        data = StudentLogoutDataDto(loggedOut = true),
    )

    override suspend fun me(authorization: String): StudentMeResponseDto = error("Not used")
}

private class FakeHomeApi : StudentHomeApi {
    var lastAuthorization: String = ""
    var offline = false
    var serviceError = false
    var cancelled = false
    var responseStudentId = "42"

    override suspend fun home(authorization: String): StudentHomeResponseDto {
        if (offline) throw IOException("offline")
        if (cancelled) throw CancellationException("cancelled")
        if (serviceError) return StudentHomeResponseDto(success = false)
        lastAuthorization = authorization
        return StudentHomeResponseDto(
            success = true,
            data = StudentHomeDataDto(
                student = HomeStudentDto(id = responseStudentId),
                smartGuide = HomeSmartGuideDto(
                    enabled = true,
                    status = "ready",
                    totalSteps = 2,
                    steps = listOf(
                        HomeSmartGuideStepDto(id = 20, sortOrder = 2, title = "الثانية"),
                        HomeSmartGuideStepDto(id = 10, sortOrder = 1, title = "الأولى"),
                    ),
                ),
            ),
        )
    }
}
