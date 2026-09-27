package app.masary.feature.subjects.data

import app.masary.core.datastore.SessionManager
import app.masary.core.local.StudentLocalStore
import app.masary.core.models.student.StudentLiveState
import app.masary.core.models.auth.AuthTokens
import app.masary.core.network.auth.StudentAuthApi
import app.masary.core.network.auth.StudentRefreshRequestDto
import app.masary.core.network.subjects.StudentSubjectSummaryDto
import app.masary.core.network.subjects.StudentSubjectsApi
import app.masary.core.network.subjects.StudentSubjectsDataDto
import app.masary.feature.subjects.domain.StudentSubject
import app.masary.feature.subjects.domain.StudentSubjectsData
import app.masary.feature.subjects.domain.SubjectAccess
import app.masary.feature.subjects.domain.SubjectAccessStatus
import app.masary.feature.subjects.domain.SubjectLastActivity
import app.masary.feature.subjects.domain.SubjectMedia
import app.masary.feature.subjects.domain.SubjectProgress
import app.masary.feature.subjects.domain.SubjectsAcademicContext
import app.masary.feature.subjects.domain.SubjectsEmptyState
import app.masary.feature.subjects.domain.SubjectsNetworkException
import app.masary.feature.subjects.domain.SubjectsRepository
import app.masary.feature.subjects.domain.SubjectsServiceException
import app.masary.feature.subjects.domain.SubjectsSessionExpiredException
import app.masary.feature.subjects.domain.SubjectsSnapshotStore
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import retrofit2.HttpException

class NetworkSubjectsRepository(
    private val subjectsApi: StudentSubjectsApi,
    private val authApi: StudentAuthApi,
    private val sessionManager: SessionManager,
    private val snapshotStore: SubjectsSnapshotStore,
    private val localStore: StudentLocalStore,
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1_000L },
) : SubjectsRepository {
    override fun observeLiveState(): Flow<StudentLiveState> =
        sessionManager.session.flatMapLatest { session ->
            session?.let { localStore.observeLiveState(it.id) } ?: flowOf(StudentLiveState())
        }

    override suspend fun loadSubjects(): Result<StudentSubjectsData> {
        val studentId = sessionManager.session.first()?.id
            ?: return Result.failure(SubjectsSessionExpiredException())
        return runCatching {
            var tokens = sessionManager.readTokens()
                ?: throw SubjectsSessionExpiredException()
            if (tokens.accessTokenNeedsRefresh(nowEpochSeconds())) {
                tokens = refreshTokens(tokens.refreshToken)
            }

            try {
                requestSubjects(tokens, studentId)
            } catch (error: HttpException) {
                if (error.code() != 401) throw error
                tokens = refreshTokens(tokens.refreshToken)
                requestSubjects(tokens, studentId)
            }.also { data ->
                try {
                    snapshotStore.write(studentId, data)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    // A valid server response must remain usable even if cache persistence fails.
                }
            }
        }.recoverCatching { error ->
            if (error is CancellationException) throw error
            throw mapFailure(error)
        }
    }

    override suspend fun loadSnapshot(): StudentSubjectsData? {
        val studentId = sessionManager.session.first()?.id ?: return null
        return try {
            snapshotStore.read(studentId)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun clearSnapshot() = snapshotStore.clear()

    private suspend fun requestSubjects(tokens: AuthTokens, expectedStudentId: String): StudentSubjectsData {
        val response = subjectsApi.subjects("Bearer ${tokens.accessToken}")
        val data = response.data
        if (!response.success || data == null) {
            val code = response.error?.code.orEmpty()
            if (code in setOf("unauthorized", "invalid_refresh_token", "refresh_expired")) {
                throw SubjectsSessionExpiredException(response.error?.message ?: "انتهت جلسة الدخول.")
            }
            throw SubjectsServiceException(response.error?.message ?: "تعذر تحميل المواد الآن.")
        }
        return data.toDomain().also { subjects ->
            if (subjects.studentId != expectedStudentId) {
                throw SubjectsSessionExpiredException("تعذر التحقق من هوية قائمة المواد.")
            }
        }
    }

    private suspend fun refreshTokens(refreshToken: String): AuthTokens {
        try {
            val response = authApi.refresh(StudentRefreshRequestDto(refreshToken))
            val data = response.data
            if (!response.success || data == null) {
                throw SubjectsSessionExpiredException(
                    response.error?.message ?: "انتهت جلسة الدخول. سجّل الدخول من جديد.",
                )
            }
            return AuthTokens(
                accessToken = data.accessToken,
                refreshToken = data.refreshToken,
                expiresInSeconds = data.expiresIn,
                accessTokenExpiresAtEpochSeconds = nowEpochSeconds() + data.expiresIn,
            ).also { sessionManager.updateTokens(it) }
        } catch (error: HttpException) {
            if (error.code() in 400..403) throw SubjectsSessionExpiredException(cause = error)
            throw error
        }
    }

    private fun mapFailure(error: Throwable): Throwable = when (error) {
        is SubjectsSessionExpiredException,
        is SubjectsNetworkException,
        is SubjectsServiceException,
        -> error
        is IOException -> SubjectsNetworkException(cause = error)
        is HttpException -> when (error.code()) {
            401, 403 -> SubjectsSessionExpiredException(cause = error)
            429 -> SubjectsServiceException("طلبات كثيرة خلال وقت قصير. انتظر قليلًا ثم حدّث المواد.", error)
            else -> SubjectsServiceException(cause = error)
        }
        else -> SubjectsServiceException(cause = error)
    }
}

internal fun StudentSubjectsDataDto.toDomain(): StudentSubjectsData = StudentSubjectsData(
    studentId = studentId,
    version = version,
    generatedAt = generatedAt,
    complete = complete,
    academic = SubjectsAcademicContext(
        available = academic.available,
        gradeName = academic.gradeName,
        departmentName = academic.departmentName,
        cityName = academic.cityName,
        curriculumName = academic.curriculumName,
        reason = academic.reason,
    ),
    subjects = subjects
        .filter { it.subjectVersionId > 0 && it.name.isNotBlank() }
        .map(StudentSubjectSummaryDto::toDomain),
    empty = SubjectsEmptyState(empty.isEmpty, empty.reason),
)

private fun StudentSubjectSummaryDto.toDomain(): StudentSubject = StudentSubject(
    subjectVersionId = subjectVersionId,
    name = name.trim(),
    hearts = hearts.coerceAtLeast(0),
    points = points?.coerceAtLeast(0),
    level = level?.coerceIn(1, 10),
    curriculumLabel = curriculumLabel.trim(),
    progress = SubjectProgress(
        available = progress.available && progress.percent != null,
        percent = progress.percent?.coerceIn(0, 100),
        reason = progress.reason,
    ),
    media = SubjectMedia(
        available = media.available && !media.key.isNullOrBlank(),
        key = media.key?.trim()?.takeIf(String::isNotBlank),
        reason = media.reason,
    ),
    access = SubjectAccess(
        available = access.available,
        status = when (access.status) {
            "available" -> SubjectAccessStatus.Available
            "free" -> SubjectAccessStatus.Free
            "requires_subscription" -> SubjectAccessStatus.RequiresSubscription
            "blocked" -> SubjectAccessStatus.Blocked
            else -> SubjectAccessStatus.Unknown
        },
        reason = access.reason,
    ),
    lastActivity = SubjectLastActivity(
        available = lastActivity.available,
        unitId = lastActivity.unitId?.takeIf { it > 0 },
        mode = lastActivity.mode,
        updatedAt = lastActivity.updatedAt,
        reason = lastActivity.reason,
    ),
)
