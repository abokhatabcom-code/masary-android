package app.masary.feature.subject.data

import app.masary.core.datastore.SessionManager
import app.masary.core.models.student.StudentLiveState
import app.masary.core.models.auth.AuthTokens
import app.masary.core.network.auth.StudentAuthApi
import app.masary.core.network.auth.StudentRefreshRequestDto
import app.masary.core.network.subject.StudentSubjectDetailDataDto
import app.masary.core.network.subject.StudentSubjectApi
import app.masary.feature.subject.domain.StudentSubjectPage
import app.masary.feature.subject.domain.SubjectAccess
import app.masary.feature.subject.domain.SubjectAccessStatus
import app.masary.feature.subject.domain.SubjectActionAvailability
import app.masary.feature.subject.domain.SubjectActions
import app.masary.feature.subject.domain.SubjectContentPart
import app.masary.feature.subject.domain.SubjectContentSummary
import app.masary.feature.subject.domain.SubjectHearts
import app.masary.feature.subject.domain.SubjectIdentity
import app.masary.feature.subject.domain.SubjectIntValue
import app.masary.feature.subject.domain.SubjectLastActivity
import app.masary.feature.subject.domain.SubjectUnit
import app.masary.feature.subject.domain.SubjectLesson
import app.masary.feature.subject.domain.SubjectLearningStatus
import app.masary.feature.subject.domain.SubjectLearningState
import app.masary.feature.subject.domain.SubjectProgressSettings
import app.masary.feature.subject.domain.SubjectLearningProgress
import app.masary.feature.subject.domain.SubjectMedia
import app.masary.feature.subject.domain.SubjectPageNetworkException
import app.masary.feature.subject.domain.SubjectPageNotFoundException
import app.masary.feature.subject.domain.SubjectPageServiceException
import app.masary.feature.subject.domain.SubjectPageSessionExpiredException
import app.masary.feature.subject.domain.SubjectProgressValue
import app.masary.feature.subject.domain.SubjectRepository
import app.masary.feature.subject.domain.SubjectRestoreTime
import app.masary.feature.subject.domain.SubjectSnapshotStore
import app.masary.feature.subject.domain.SubjectStructureMode
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import retrofit2.HttpException

class NetworkSubjectRepository(
    private val subjectApi: StudentSubjectApi,
    private val authApi: StudentAuthApi,
    private val sessionManager: SessionManager,
    private val snapshotStore: SubjectSnapshotStore,
    private val liveStateProvider: (String) -> Flow<StudentLiveState> = { flowOf(StudentLiveState()) },
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1_000L },
) : SubjectRepository {
    override fun observeLiveState(): Flow<StudentLiveState> =
        sessionManager.session.flatMapLatest { session ->
            session?.let { liveStateProvider(it.id) } ?: flowOf(StudentLiveState())
        }

    override suspend fun loadSubject(subjectVersionId: Int): Result<StudentSubjectPage> {
        if (subjectVersionId <= 0) return Result.failure(SubjectPageNotFoundException())
        val studentId = sessionManager.session.first()?.id
            ?: return Result.failure(SubjectPageSessionExpiredException())
        return runCatching {
            var tokens = sessionManager.readTokens() ?: throw SubjectPageSessionExpiredException()
            if (tokens.accessTokenNeedsRefresh(nowEpochSeconds())) {
                tokens = refreshTokens(tokens.refreshToken)
            }
            try {
                requestSubject(tokens, studentId, subjectVersionId)
            } catch (error: HttpException) {
                if (error.code() != 401) throw error
                tokens = refreshTokens(tokens.refreshToken)
                requestSubject(tokens, studentId, subjectVersionId)
            }.also { data ->
                try {
                    snapshotStore.write(studentId, subjectVersionId, data)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    // A valid server response remains usable when cache persistence fails.
                }
            }
        }.recoverCatching { error ->
            if (error is CancellationException) throw error
            throw mapFailure(error)
        }
    }

    override suspend fun loadSnapshot(subjectVersionId: Int): StudentSubjectPage? {
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

    private suspend fun requestSubject(
        tokens: AuthTokens,
        expectedStudentId: String,
        expectedSubjectVersionId: Int,
    ): StudentSubjectPage {
        val response = subjectApi.subject(
            authorization = "Bearer ${tokens.accessToken}",
            subjectVersionId = expectedSubjectVersionId,
        )
        val data = response.data
        if (!response.success || data == null) {
            val code = response.error?.code.orEmpty()
            when (code) {
                "unauthorized", "invalid_refresh_token", "refresh_expired" ->
                    throw SubjectPageSessionExpiredException(response.error?.message ?: "انتهت جلسة الدخول.")
                "subject_not_found", "invalid_subject" ->
                    throw SubjectPageNotFoundException(response.error?.message ?: "المادة غير متاحة لهذا الحساب.")
                else -> throw SubjectPageServiceException(response.error?.message ?: "تعذر تحميل المادة الآن.")
            }
        }
        return data.toDomain().also { subject ->
            if (subject.studentId != expectedStudentId) {
                throw SubjectPageSessionExpiredException("تعذر التحقق من هوية بيانات المادة.")
            }
            if (subject.subjectVersionId != expectedSubjectVersionId) {
                throw SubjectPageNotFoundException("تعذر التحقق من معرف المادة.")
            }
        }
    }

    private suspend fun refreshTokens(refreshToken: String): AuthTokens {
        try {
            val response = authApi.refresh(StudentRefreshRequestDto(refreshToken))
            val data = response.data
            if (!response.success || data == null) {
                throw SubjectPageSessionExpiredException(response.error?.message ?: "انتهت جلسة الدخول.")
            }
            return AuthTokens(
                accessToken = data.accessToken,
                refreshToken = data.refreshToken,
                expiresInSeconds = data.expiresIn,
                accessTokenExpiresAtEpochSeconds = nowEpochSeconds() + data.expiresIn,
            ).also { sessionManager.updateTokens(it) }
        } catch (error: HttpException) {
            if (error.code() in 400..403) throw SubjectPageSessionExpiredException(cause = error)
            throw error
        }
    }

    private fun mapFailure(error: Throwable): Throwable = when (error) {
        is SubjectPageSessionExpiredException,
        is SubjectPageNotFoundException,
        is SubjectPageNetworkException,
        is SubjectPageServiceException,
        -> error
        is IOException -> SubjectPageNetworkException(error)
        is HttpException -> when (error.code()) {
            401, 403 -> SubjectPageSessionExpiredException(cause = error)
            404, 422 -> SubjectPageNotFoundException(cause = error)
            429 -> SubjectPageServiceException("طلبات كثيرة خلال وقت قصير. انتظر قليلًا ثم حدّث الصفحة.", error)
            else -> SubjectPageServiceException(cause = error)
        }
        else -> SubjectPageServiceException(cause = error)
    }
}

internal fun StudentSubjectDetailDataDto.toDomain(): StudentSubjectPage = StudentSubjectPage(
    studentId = studentId,
    subjectVersionId = subjectVersionId,
    generatedAt = generatedAt,
    identity = SubjectIdentity(
        name = identity.name.trim(),
        curriculumLabel = identity.curriculumLabel.trim(),
        versionType = identity.versionType.trim(),
        media = SubjectMedia(
            available = identity.media.available && !identity.media.key.isNullOrBlank(),
            key = identity.media.key?.trim()?.takeIf(String::isNotBlank),
            reason = identity.media.reason,
        ),
    ),
    points = SubjectIntValue(points.available && points.value != null, points.value?.coerceAtLeast(0), points.reason),
    level = SubjectIntValue(level.available && level.value != null, level.value?.coerceAtLeast(0), level.reason),
    progress = SubjectProgressValue(
        progress.available && progress.percent != null,
        progress.percent?.coerceIn(0, 100),
        progress.reason,
    ),
    hearts = SubjectHearts(
        current = hearts.current.coerceIn(0, hearts.maximum.coerceAtLeast(1)),
        maximum = hearts.maximum.coerceAtLeast(1),
        nextRestore = SubjectRestoreTime(
            hearts.nextRestore.available && !hearts.nextRestore.at.isNullOrBlank(),
            hearts.nextRestore.at?.trim()?.takeIf(String::isNotBlank),
            hearts.nextRestore.reason,
        ),
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
    content = SubjectContentSummary(
        structureMode = when (content.structureMode) {
            "units" -> SubjectStructureMode.Units
            "lessons" -> SubjectStructureMode.Lessons
            else -> SubjectStructureMode.Unknown
        },
        hasParts = content.hasParts,
        parts = content.parts
            .filter { it.partNumber >= 0 }
            .map {
                SubjectContentPart(
                    partNumber = it.partNumber,
                    label = it.label.trim().ifBlank { if (it.partNumber > 0) "الجزء ${it.partNumber}" else "المحتوى" },
                    unitsCount = it.unitsCount.coerceAtLeast(0),
                    lessonsCount = it.lessonsCount.coerceAtLeast(0),
                )
            },
        detailsAvailable = content.detailsAvailable,
        units = content.units
            .filter { it.id > 0 }
            .map { unit ->
                SubjectUnit(
                    id = unit.id,
                    partNumber = unit.partNumber.coerceAtLeast(0),
                    title = unit.title.trim().ifBlank { "وحدة ${unit.id}" },
                    position = unit.position.coerceAtLeast(0),
                    state = unit.state.toDomainLearningState(),
                    progress = unit.progress.toDomainLearningProgress(),
                    review = app.masary.feature.subject.domain.SubjectUnitReview(
                        visible = unit.review.visible,
                        available = unit.review.available,
                        mistakesCount = unit.review.mistakesCount.coerceAtLeast(0),
                        reason = unit.review.reason.trim(),
                    ),
                    lessons = unit.lessons
                        .filter { it.id > 0 }
                        .map { lesson -> lesson.toDomainLesson() },
                )
            },
        lessons = content.lessons
            .filter { it.id > 0 }
            .map { lesson -> lesson.toDomainLesson() },
        progressSettings = SubjectProgressSettings(
            progressMode = content.progressSettings.progressMode.trim().ifBlank { "unit" },
            unlockMode = content.progressSettings.unlockMode.trim().ifBlank { "sequential" },
            unlockThresholdPercent = content.progressSettings.unlockThresholdPercent.coerceIn(0.0, 100.0),
            reviewProgressCapPoints = content.progressSettings.reviewProgressCapPoints.coerceAtLeast(1.0),
        ),
        reason = content.reason,
    ),
    lastActivity = SubjectLastActivity(
        available = lastActivity.available,
        unitId = lastActivity.unitId?.takeIf { it > 0 },
        lessonId = lastActivity.lessonId?.takeIf { it > 0 },
        mode = lastActivity.mode.trim(),
        updatedAt = lastActivity.updatedAt.trim(),
        preparation = SubjectActionAvailability(lastActivity.preparation.available, lastActivity.preparation.reason),
        reason = lastActivity.reason,
    ),
    actions = SubjectActions(
        trainingCenter = SubjectActionAvailability(actions.trainingCenter.available, actions.trainingCenter.reason),
    ),
    version = version,
)


private fun app.masary.core.network.subject.SubjectLearningStateDto.toDomainLearningState(): SubjectLearningState =
    SubjectLearningState(
        status = when (status.trim().lowercase()) {
            "ready" -> SubjectLearningStatus.Ready
            "in_progress" -> SubjectLearningStatus.InProgress
            "completed" -> SubjectLearningStatus.Completed
            "locked" -> SubjectLearningStatus.Locked
            "unavailable" -> SubjectLearningStatus.Unavailable
            else -> SubjectLearningStatus.Unknown
        },
        reason = reason.trim(),
    )

private fun app.masary.core.network.subject.SubjectLessonDto.toDomainLesson(): SubjectLesson =
    SubjectLesson(
        id = id,
        unitId = unitId?.takeIf { it > 0 },
        partNumber = partNumber.coerceAtLeast(0),
        title = title.trim().ifBlank { "درس $id" },
        position = position.coerceAtLeast(0),
        state = state.toDomainLearningState(),
        preparation = SubjectActionAvailability(
            available = preparation.available,
            reason = preparation.reason.trim(),
        ),
        progress = progress.toDomainLearningProgress(),
    )


private fun app.masary.core.network.subject.SubjectLearningProgressDto.toDomainLearningProgress():
    SubjectLearningProgress = SubjectLearningProgress(
        reviewPercent = reviewPercent.coerceIn(0.0, 100.0),
        unlockThresholdPercent = unlockThresholdPercent.coerceIn(0.0, 100.0),
        learnCompleted = learnCompleted,
    )
