package app.masary.feature.trainingcenter.data

import app.masary.core.datastore.SessionManager
import app.masary.core.models.auth.AuthTokens
import app.masary.core.network.auth.StudentAuthApi
import app.masary.core.network.auth.StudentRefreshRequestDto
import app.masary.core.network.training.StudentTrainingCenterApi
import app.masary.core.network.training.StudentTrainingCenterDataDto
import app.masary.core.network.training.TrainingCenterToolDto
import app.masary.feature.trainingcenter.domain.StudentTrainingCenter
import app.masary.feature.trainingcenter.domain.TrainingCenterIdentity
import app.masary.feature.trainingcenter.domain.TrainingCenterNetworkException
import app.masary.feature.trainingcenter.domain.TrainingCenterNotFoundException
import app.masary.feature.trainingcenter.domain.TrainingCenterRepository
import app.masary.feature.trainingcenter.domain.TrainingCenterServiceException
import app.masary.feature.trainingcenter.domain.TrainingCenterSessionExpiredException
import app.masary.feature.trainingcenter.domain.TrainingCenterSnapshotStore
import app.masary.feature.trainingcenter.domain.TrainingCenterTool
import app.masary.feature.trainingcenter.domain.TrainingToolKey
import app.masary.feature.trainingcenter.domain.TrainingToolStatus
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import retrofit2.HttpException

class NetworkTrainingCenterRepository(
    private val trainingCenterApi: StudentTrainingCenterApi,
    private val authApi: StudentAuthApi,
    private val sessionManager: SessionManager,
    private val snapshotStore: TrainingCenterSnapshotStore,
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1_000L },
) : TrainingCenterRepository {
    override suspend fun loadTrainingCenter(subjectVersionId: Int): Result<StudentTrainingCenter> {
        if (subjectVersionId <= 0) return Result.failure(TrainingCenterNotFoundException())
        val studentId = sessionManager.session.first()?.id
            ?: return Result.failure(TrainingCenterSessionExpiredException())
        return runCatching {
            var tokens = sessionManager.readTokens() ?: throw TrainingCenterSessionExpiredException()
            if (tokens.accessTokenNeedsRefresh(nowEpochSeconds())) {
                tokens = refreshTokens(tokens.refreshToken)
            }
            try {
                requestTrainingCenter(tokens, studentId, subjectVersionId)
            } catch (error: HttpException) {
                if (error.code() != 401) throw error
                tokens = refreshTokens(tokens.refreshToken)
                requestTrainingCenter(tokens, studentId, subjectVersionId)
            }.also { data ->
                try {
                    snapshotStore.write(studentId, subjectVersionId, data)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    // A verified response remains usable when local persistence fails.
                }
            }
        }.recoverCatching { error ->
            if (error is CancellationException) throw error
            throw mapFailure(error)
        }
    }

    override suspend fun loadSnapshot(subjectVersionId: Int): StudentTrainingCenter? {
        if (subjectVersionId <= 0) return null
        val studentId = sessionManager.session.first()?.id ?: return null
        return try {
            snapshotStore.read(studentId, subjectVersionId)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun clearSnapshots() = snapshotStore.clear()

    private suspend fun requestTrainingCenter(
        tokens: AuthTokens,
        expectedStudentId: String,
        expectedSubjectVersionId: Int,
    ): StudentTrainingCenter {
        val response = trainingCenterApi.trainingCenter(
            authorization = "Bearer ${tokens.accessToken}",
            subjectVersionId = expectedSubjectVersionId,
        )
        val data = response.data
        if (!response.success || data == null) {
            val code = response.error?.code.orEmpty()
            when (code) {
                "unauthorized", "invalid_refresh_token", "refresh_expired" ->
                    throw TrainingCenterSessionExpiredException(
                        response.error?.message ?: "انتهت جلسة الدخول.",
                    )
                "subject_not_found", "invalid_subject" ->
                    throw TrainingCenterNotFoundException(
                        response.error?.message ?: "مركز التدريب غير متاح لهذه المادة.",
                    )
                else -> throw TrainingCenterServiceException(
                    response.error?.message ?: "تعذر تحميل مركز التدريب الآن.",
                )
            }
        }
        return data.toDomain().also { center ->
            if (center.studentId != expectedStudentId) {
                throw TrainingCenterSessionExpiredException("تعذر التحقق من هوية بيانات مركز التدريب.")
            }
            if (center.subjectVersionId != expectedSubjectVersionId) {
                throw TrainingCenterNotFoundException("تعذر التحقق من معرف مادة مركز التدريب.")
            }
            if (center.identity.name.isBlank() || center.version.isBlank()) {
                throw TrainingCenterServiceException("استجابة مركز التدريب غير مكتملة.")
            }
            val keys = center.tools.map(TrainingCenterTool::key)
            if (keys.size != TrainingToolKey.ordered.size || keys.toSet() != TrainingToolKey.ordered.toSet()) {
                throw TrainingCenterServiceException("تعذر التحقق من أدوات مركز التدريب.")
            }
        }
    }

    private suspend fun refreshTokens(refreshToken: String): AuthTokens {
        try {
            val response = authApi.refresh(StudentRefreshRequestDto(refreshToken))
            val data = response.data
            if (!response.success || data == null) {
                throw TrainingCenterSessionExpiredException(
                    response.error?.message ?: "انتهت جلسة الدخول.",
                )
            }
            return AuthTokens(
                accessToken = data.accessToken,
                refreshToken = data.refreshToken,
                expiresInSeconds = data.expiresIn,
                accessTokenExpiresAtEpochSeconds = nowEpochSeconds() + data.expiresIn,
            ).also { sessionManager.updateTokens(it) }
        } catch (error: HttpException) {
            if (error.code() in 400..403) throw TrainingCenterSessionExpiredException(cause = error)
            throw error
        }
    }

    private fun mapFailure(error: Throwable): Throwable = when (error) {
        is TrainingCenterSessionExpiredException,
        is TrainingCenterNotFoundException,
        is TrainingCenterNetworkException,
        is TrainingCenterServiceException,
        -> error
        is IOException -> TrainingCenterNetworkException(error)
        is HttpException -> when (error.code()) {
            401, 403 -> TrainingCenterSessionExpiredException(cause = error)
            404, 422 -> TrainingCenterNotFoundException(cause = error)
            429 -> TrainingCenterServiceException(
                "طلبات كثيرة خلال وقت قصير. انتظر قليلًا ثم حدّث الصفحة.",
                error,
            )
            else -> TrainingCenterServiceException(cause = error)
        }
        else -> TrainingCenterServiceException(cause = error)
    }
}

internal fun StudentTrainingCenterDataDto.toDomain(): StudentTrainingCenter = StudentTrainingCenter(
    studentId = studentId.trim(),
    subjectVersionId = subjectVersionId,
    generatedAt = generatedAt.trim(),
    identity = TrainingCenterIdentity(
        name = identity.name.trim(),
        curriculumLabel = identity.curriculumLabel.trim(),
    ),
    tools = tools.map(TrainingCenterToolDto::toDomain).sortedBy { it.key.order },
    version = version.trim(),
)

private fun TrainingCenterToolDto.toDomain(): TrainingCenterTool {
    val stableKey = TrainingToolKey.fromWire(key)
        ?: throw TrainingCenterServiceException("أعاد الخادم أداة تدريب غير معروفة.")
    if (
        activityType.trim() != stableKey.activityType ||
        activityMode.trim() != stableKey.activityMode ||
        source.trim() != stableKey.source
    ) {
        throw TrainingCenterServiceException("عقد أداة التدريب ${stableKey.wireKey} غير متطابق.")
    }
    val stableStatus = when (status.trim().lowercase()) {
        "ready" -> TrainingToolStatus.Ready
        "empty" -> TrainingToolStatus.Empty
        "source_unavailable" -> TrainingToolStatus.SourceUnavailable
        else -> throw TrainingCenterServiceException("حالة أداة التدريب غير معروفة.")
    }
    val safeCount = itemCount?.coerceAtLeast(0)
    if (available && (stableStatus != TrainingToolStatus.Ready || safeCount == null || safeCount <= 0)) {
        throw TrainingCenterServiceException("توفر أداة التدريب لا يطابق عدد عناصرها.")
    }
    if (!available && stableStatus == TrainingToolStatus.Ready) {
        throw TrainingCenterServiceException("حالة أداة التدريب الجاهزة غير متطابقة.")
    }
    return TrainingCenterTool(
        key = stableKey,
        title = title.trim().ifBlank { throw TrainingCenterServiceException("اسم أداة التدريب مفقود.") },
        description = description.trim().ifBlank {
            throw TrainingCenterServiceException("وصف أداة التدريب مفقود.")
        },
        available = available,
        status = stableStatus,
        reason = reason.trim(),
        itemCount = safeCount,
    )
}
