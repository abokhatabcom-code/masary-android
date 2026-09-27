package app.masary.feature.home.data

import app.masary.core.datastore.SessionManager
import app.masary.core.models.student.StudentLiveState
import app.masary.core.models.auth.AuthTokens
import app.masary.core.network.auth.StudentAuthApi
import app.masary.core.network.auth.StudentRefreshRequestDto
import app.masary.core.network.home.HomeContinueLearningDto
import app.masary.core.network.home.HomeSmartGuideDto
import app.masary.core.network.home.HomeSmartGuideStepDto
import app.masary.core.network.home.HomeStreakDto
import app.masary.core.network.home.HomeStreakGoalDto
import app.masary.core.network.home.StudentHomeApi
import app.masary.core.network.home.StudentHomeDataDto
import app.masary.feature.home.domain.HomeContinueLearning
import app.masary.feature.home.domain.HomeNetworkException
import app.masary.feature.home.domain.HomeNotifications
import app.masary.feature.home.domain.HomeRepository
import app.masary.feature.home.domain.HomeSnapshotStore
import app.masary.feature.home.domain.HomeServiceException
import app.masary.feature.home.domain.HomeSessionExpiredException
import app.masary.feature.home.domain.HomeSmartGuide
import app.masary.feature.home.domain.HomeSmartGuideStep
import app.masary.feature.home.domain.HomeStreak
import app.masary.feature.home.domain.HomeStreakGoal
import app.masary.feature.home.domain.HomeStudent
import app.masary.feature.home.domain.HomeSubscription
import app.masary.feature.home.domain.HomeSummary
import app.masary.feature.home.domain.HomeToday
import app.masary.feature.home.domain.HomeIndicators
import app.masary.feature.home.domain.HomeSubject
import app.masary.feature.home.domain.HomeSpotlight
import app.masary.feature.home.domain.StudentHomeData
import java.io.IOException
import retrofit2.HttpException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.CancellationException

class NetworkHomeRepository(
    private val homeApi: StudentHomeApi,
    private val authApi: StudentAuthApi,
    private val sessionManager: SessionManager,
    private val snapshotStore: HomeSnapshotStore,
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1_000L },
    private val liveStateProvider: (String) -> Flow<StudentLiveState> = { flowOf(StudentLiveState()) },
) : HomeRepository {

    override fun observeLiveState(): Flow<StudentLiveState> =
        sessionManager.session.flatMapLatest { session ->
            session?.let { liveStateProvider(it.id) } ?: flowOf(StudentLiveState())
        }

    override suspend fun loadHome(): Result<StudentHomeData> {
        val studentId = sessionManager.session.first()?.id ?: return Result.failure(HomeSessionExpiredException())
        return runCatching {
            var tokens = sessionManager.readTokens()
                ?: throw HomeSessionExpiredException()

            if (tokens.accessTokenNeedsRefresh(nowEpochSeconds())) {
                tokens = refreshTokens(tokens.refreshToken)
            }

            try {
                requestHome(tokens, studentId)
            } catch (error: HttpException) {
                if (error.code() != 401) throw error
                tokens = refreshTokens(tokens.refreshToken)
                requestHome(tokens, studentId)
            }.also { data ->
                try {
                    snapshotStore.write(studentId, data)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    // A cache write must never turn a valid platform response into an error.
                }
            }
        }.recoverCatching { error ->
            if (error is CancellationException) throw error
            throw mapFailure(error)
        }
    }

    override suspend fun loadSnapshot(): StudentHomeData? {
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

    private suspend fun requestHome(tokens: AuthTokens, expectedStudentId: String): StudentHomeData {
        val response = homeApi.home("Bearer ${tokens.accessToken}")
        val data = response.data
        if (!response.success || data == null) {
            val code = response.error?.code.orEmpty()
            if (code in setOf("unauthorized", "invalid_refresh_token", "refresh_expired")) {
                throw HomeSessionExpiredException(response.error?.message ?: "انتهت جلسة الدخول.")
            }
            throw HomeServiceException(response.error?.message ?: "تعذر تحميل الصفحة الرئيسية الآن.")
        }
        return data.toDomain().also { home ->
            if (home.student.id != expectedStudentId) {
                throw HomeSessionExpiredException("تعذر التحقق من هوية بيانات الصفحة الرئيسية.")
            }
        }
    }

    private suspend fun refreshTokens(refreshToken: String): AuthTokens {
        try {
            val response = authApi.refresh(StudentRefreshRequestDto(refreshToken))
            val data = response.data
            if (!response.success || data == null) {
                throw HomeSessionExpiredException(
                    response.error?.message ?: "انتهت جلسة الدخول. سجّل الدخول من جديد.",
                )
            }
            val refreshed = AuthTokens(
                accessToken = data.accessToken,
                refreshToken = data.refreshToken,
                expiresInSeconds = data.expiresIn,
                accessTokenExpiresAtEpochSeconds = nowEpochSeconds() + data.expiresIn,
            )
            sessionManager.updateTokens(refreshed)
            return refreshed
        } catch (error: HttpException) {
            if (error.code() in 400..403) {
                throw HomeSessionExpiredException(cause = error)
            }
            throw error
        }
    }

    private fun mapFailure(error: Throwable): Throwable = when (error) {
        is HomeSessionExpiredException,
        is HomeNetworkException,
        is HomeServiceException,
        -> error

        is IOException -> HomeNetworkException(cause = error)
        is HttpException -> when (error.code()) {
            401, 403 -> HomeSessionExpiredException(cause = error)
            429 -> HomeServiceException("طلبات كثيرة خلال وقت قصير. انتظر قليلًا ثم حدّث الصفحة.", error)
            else -> HomeServiceException(cause = error)
        }
        else -> HomeServiceException(cause = error)
    }

}

private fun StudentHomeDataDto.toDomain(): StudentHomeData = StudentHomeData(
    version = version,
    generatedAt = generatedAt,
    student = HomeStudent(
        id = student.id,
        username = student.username,
        displayName = student.displayName,
        avatarPath = student.avatarPath,
    ),
    summary = HomeSummary(
        globalXp = summary.globalXp.coerceAtLeast(0),
        gems = summary.gems.coerceAtLeast(0),
        level = summary.level.coerceAtLeast(1),
        levelPercent = summary.levelPercent.coerceIn(0, 100),
        levelNextXp = summary.levelNextXp.coerceAtLeast(0),
    ),
    streak = streak.toDomain(),
    today = HomeToday(
        xp = today.xp.coerceAtLeast(0),
        seconds = today.seconds.coerceAtLeast(0),
        minutes = today.minutes.coerceAtLeast(0),
        attempts = today.attempts.coerceAtLeast(0),
    ),
    subscription = HomeSubscription(
        active = subscription.active,
        status = subscription.status,
        endsAt = subscription.endsAt,
    ),
    notifications = HomeNotifications(notifications.unreadCount.coerceAtLeast(0)),
    continueLearning = continueLearning.toDomain(),
    smartGuide = smartGuide.toDomain(),
    indicators = HomeIndicators(
        totalXp = indicators.totalXp.coerceAtLeast(0),
        gems = indicators.gems.coerceAtLeast(0),
        streakDays = indicators.streakDays.coerceAtLeast(0),
        globalRank = indicators.globalRank?.takeIf { it > 0 },
    ),
    subjects = subjects.filter { it.subjectVersionId > 0 }.map {
        HomeSubject(
            subjectVersionId = it.subjectVersionId,
            name = it.name,
            hearts = it.hearts.coerceAtLeast(0),
            progressPercent = it.progressPercent?.coerceIn(0, 100),
            points = it.points?.coerceAtLeast(0),
            level = it.level?.coerceIn(1, 10),
        )
    },
    spotlight = spotlight?.takeIf { it.title.isNotBlank() }?.let {
        HomeSpotlight(it.type, it.title, it.body, it.ctaLabel, it.ctaUrl)
    },
)

private fun HomeStreakDto.toDomain(): HomeStreak = HomeStreak(
    currentDays = currentDays.coerceAtLeast(0),
    bestDays = bestDays.coerceAtLeast(0),
    protectionCount = protectionCount.coerceAtLeast(0),
    protectionMax = protectionMax.coerceAtLeast(0),
    nextMilestone = nextMilestone.coerceAtLeast(0),
    checkpointDays = checkpointDays.coerceAtLeast(0),
    status = status,
    message = message,
    goal = goal.toDomain(),
)

private fun HomeStreakGoalDto.toDomain(): HomeStreakGoal = HomeStreakGoal(
    days = days.coerceAtLeast(0),
    status = status,
    isCompleted = isCompleted,
    remainingDays = remainingDays.coerceAtLeast(0),
    progressPercent = progressPercent.coerceIn(0, 100),
    gems = gems.coerceAtLeast(0),
    shields = shields.coerceAtLeast(0),
    label = label,
)

private fun HomeContinueLearningDto.toDomain(): HomeContinueLearning = HomeContinueLearning(
    available = available,
    subjectVersionId = subjectVersionId,
    subjectName = subjectName,
    unitId = unitId,
    unitTitle = unitTitle,
    mode = mode,
    label = label,
    hint = hint,
    disabled = disabled,
    disabledReason = disabledReason,
    hearts = hearts?.coerceAtLeast(0),
    updatedAt = updatedAt,
)

private fun HomeSmartGuideDto.toDomain(): HomeSmartGuide = HomeSmartGuide(
    enabled = enabled,
    status = status,
    guideId = guideId,
    guideDate = guideDate,
    headline = headline,
    introText = introText,
    boostNote = boostNote,
    profileKey = profileKey,
    completionPercent = completionPercent.coerceIn(0, 100),
    completedSteps = completedSteps.coerceAtLeast(0),
    totalSteps = totalSteps.coerceAtLeast(0),
    isComplete = isComplete,
    updatedAt = updatedAt,
    steps = steps.sortedWith(compareBy(HomeSmartGuideStepDto::sortOrder, HomeSmartGuideStepDto::id))
        .map(HomeSmartGuideStepDto::toDomain),
)

private fun HomeSmartGuideStepDto.toDomain(): HomeSmartGuideStep = HomeSmartGuideStep(
    id = id,
    sortOrder = sortOrder,
    subjectVersionId = subjectVersionId,
    subjectName = subjectName,
    unitId = unitId,
    part = part.coerceAtLeast(0),
    actionKey = actionKey,
    actionGroup = actionGroup,
    title = title,
    subtitle = subtitle,
    reasonText = reasonText,
    ctaLabel = ctaLabel,
    estimatedMinutes = estimatedMinutes.coerceAtLeast(0),
    rewardGems = rewardGems.coerceAtLeast(0),
    progressState = progressState,
    completedAt = completedAt,
)
