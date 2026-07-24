package app.masary.feature.activitypreparation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.masary.core.datastore.SessionManager
import app.masary.core.models.auth.AuthTokens
import app.masary.core.network.MasaryNetwork
import app.masary.core.network.activity.ActivityPreparationPreviewDataDto
import app.masary.core.network.activity.ActivityPreparationRequestDto
import app.masary.core.network.activity.ActivityStartDataDto
import app.masary.core.network.activity.StudentActivityPreparationApi
import app.masary.core.network.auth.StudentAuthApi
import app.masary.core.network.auth.StudentRefreshRequestDto
import app.masary.core.ui.MasaryColors
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import retrofit2.HttpException

@Serializable
data class ActivityPreparationDestination(
    val subjectVersionId: Int,
    val unitId: Int? = null,
    val lessonId: Int? = null,
    val activityType: String,
    val activityMode: String,
    val guideStepId: Int? = null,
    val source: String,
) {
    fun toRequest(): ActivityPreparationRequest = ActivityPreparationRequest(
        subjectVersionId = subjectVersionId,
        unitId = unitId,
        lessonId = lessonId,
        activityType = activityType,
        activityMode = activityMode,
        guideStepId = guideStepId,
        source = source,
    )

    companion object {
        fun fromGuide(
            subjectVersionId: Int,
            unitId: Int?,
            actionKey: String,
            guideStepId: Int?,
        ): ActivityPreparationDestination {
            val request = ActivityPreparationRequest.fromGuide(
                subjectVersionId = subjectVersionId,
                unitId = unitId,
                actionKey = actionKey,
                guideStepId = guideStepId,
            )
            return ActivityPreparationDestination(
                subjectVersionId = request.subjectVersionId,
                unitId = request.unitId,
                lessonId = request.lessonId,
                activityType = request.activityType,
                activityMode = request.activityMode,
                guideStepId = request.guideStepId,
                source = request.source,
            )
        }
    }
}

data class ActivityPreparationRequest(
    val subjectVersionId: Int,
    val unitId: Int? = null,
    val lessonId: Int? = null,
    val activityType: String,
    val activityMode: String,
    val guideStepId: Int? = null,
    val source: String,
) {
    init {
        require(subjectVersionId > 0)
        require(unitId == null || unitId > 0)
        require(lessonId == null || lessonId > 0)
        require(guideStepId == null || guideStepId > 0)
        require(activityType in SUPPORTED_TYPES)
        require(activityMode in SUPPORTED_MODES)
        require(source in SUPPORTED_SOURCES)
    }

    val fingerprint: String
        get() {
            val canonical = listOf(
                subjectVersionId,
                unitId ?: 0,
                lessonId ?: 0,
                activityType,
                activityMode,
                guideStepId ?: 0,
                source,
            ).joinToString("|")
            return MessageDigest.getInstance("SHA-256")
                .digest(canonical.toByteArray(Charsets.UTF_8))
                .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        }

    companion object {
        val SUPPORTED_TYPES = setOf(
            "guide_step",
            "unit_test",
            "lesson_practice",
            "review",
            "smart_review",
            "speed_test",
        )
        val SUPPORTED_MODES = setOf("learn", "practice", "review", "test", "speed")
        val SUPPORTED_SOURCES = setOf("home_guide", "guide", "subject", "unit", "lesson", "review")

        fun fromGuide(
            subjectVersionId: Int,
            unitId: Int?,
            actionKey: String,
            guideStepId: Int?,
        ): ActivityPreparationRequest {
            val normalized = actionKey.trim().lowercase()
            val type = when {
                "speed" in normalized -> "speed_test"
                "mistake" in normalized || "error" in normalized || "review" in normalized -> "review"
                "test" in normalized || "quiz" in normalized || "attempt" in normalized -> "unit_test"
                else -> "guide_step"
            }
            val mode = when (type) {
                "speed_test" -> "speed"
                "review", "smart_review" -> "review"
                "unit_test" -> "test"
                else -> "learn"
            }
            return ActivityPreparationRequest(
                subjectVersionId = subjectVersionId,
                unitId = unitId,
                activityType = type,
                activityMode = mode,
                guideStepId = guideStepId,
                source = "home_guide",
            )
        }
    }
}

data class ActivityDescriptor(
    val subjectVersionId: Int,
    val subjectName: String,
    val unitId: Int?,
    val unitTitle: String,
    val lessonId: Int?,
    val lessonTitle: String,
    val activityType: String,
    val activityMode: String,
    val title: String,
    val estimatedMinutes: Int?,
    val questionCount: Int?,
)

data class ActivityEligibility(
    val available: Boolean,
    val status: String,
    val reason: String,
    val reasonCode: String,
)

data class ActivityBalances(val hearts: Int, val gems: Int)
data class ActivityCost(val requiredHearts: Int, val heartCost: Int, val gemCost: Int)
data class ActivityAttempts(
    val available: Boolean,
    val used: Int?,
    val remaining: Int?,
    val maximum: Int?,
    val reason: String,
)
data class ActivityResume(
    val available: Boolean,
    val sessionId: String?,
    val status: String,
    val expiresAt: String,
    val reason: String,
)
data class ActivityPreparationPreview(
    val version: String,
    val generatedAt: String,
    val activity: ActivityDescriptor,
    val eligibility: ActivityEligibility,
    val balances: ActivityBalances,
    val cost: ActivityCost,
    val attempts: ActivityAttempts,
    val resume: ActivityResume,
)
data class ActivityDebit(val heartDebited: Int, val gemsDebited: Int)
data class ActivityStartResult(
    val sessionId: String,
    val status: String,
    val destination: String,
    val replayed: Boolean,
    val debit: ActivityDebit,
    val balances: ActivityBalances,
    val expiresAt: String,
)
data class PendingActivityStart(
    val idempotencyKey: String,
    val requestFingerprint: String,
    val createdAtEpochMillis: Long,
)

interface ActivityPreparationRepository {
    suspend fun preview(request: ActivityPreparationRequest): Result<ActivityPreparationPreview>
    suspend fun start(request: ActivityPreparationRequest, idempotencyKey: String): Result<ActivityStartResult>
    suspend fun startStatus(idempotencyKey: String): Result<ActivityStartResult>
}

interface ActivityPreparationPendingStore {
    suspend fun read(): PendingActivityStart?
    suspend fun write(value: PendingActivityStart)
    suspend fun clear()
}

open class ActivityPreparationException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
class ActivityPreparationNetworkException(cause: Throwable? = null) :
    ActivityPreparationException("تعذر الاتصال بالخادم. تحقق من الإنترنت ثم حاول مجددًا.", cause)
class ActivityPreparationServiceException(message: String = "تعذر تجهيز النشاط الآن.", cause: Throwable? = null) :
    ActivityPreparationException(message, cause)
class ActivityPreparationSessionExpiredException(cause: Throwable? = null) :
    ActivityPreparationException("انتهت جلسة الدخول.", cause)
class ActivityPreparationIdempotencyConflictException(cause: Throwable? = null) :
    ActivityPreparationException("تعارضت محاولة البدء السابقة. أعد فتح النشاط.", cause)
class ActivityPreparationStartNotFoundException(cause: Throwable? = null) :
    ActivityPreparationException("لم تُسجّل محاولة البدء بعد.", cause)

private class DataStorePendingActivityStartStore(
    private val dataStore: DataStore<Preferences>,
    private val now: () -> Long = System::currentTimeMillis,
) : ActivityPreparationPendingStore {
    override suspend fun read(): PendingActivityStart? {
        val values = dataStore.data.first()
        val key = values[KEY].orEmpty()
        val fingerprint = values[FINGERPRINT].orEmpty()
        val createdAt = values[CREATED_AT] ?: 0L
        if (key.isBlank() || fingerprint.isBlank() || createdAt <= 0L || now() - createdAt > MAX_AGE_MS) {
            if (key.isNotBlank() || fingerprint.isNotBlank() || createdAt > 0L) clear()
            return null
        }
        return PendingActivityStart(key, fingerprint, createdAt)
    }

    override suspend fun write(value: PendingActivityStart) {
        dataStore.edit { preferences ->
            preferences[KEY] = value.idempotencyKey
            preferences[FINGERPRINT] = value.requestFingerprint
            preferences[CREATED_AT] = value.createdAtEpochMillis
        }
    }

    override suspend fun clear() {
        dataStore.edit { preferences ->
            preferences.remove(KEY)
            preferences.remove(FINGERPRINT)
            preferences.remove(CREATED_AT)
        }
    }

    private companion object {
        val KEY = stringPreferencesKey("activity_start_key")
        val FINGERPRINT = stringPreferencesKey("activity_start_fingerprint")
        val CREATED_AT = longPreferencesKey("activity_start_created_at")
        const val MAX_AGE_MS = 6 * 60 * 60 * 1000L
    }
}

private class NetworkActivityPreparationRepository(
    private val api: StudentActivityPreparationApi,
    private val authApi: StudentAuthApi,
    private val sessionManager: SessionManager,
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1_000L },
) : ActivityPreparationRepository {
    override suspend fun preview(request: ActivityPreparationRequest): Result<ActivityPreparationPreview> =
        runRequest { token ->
            val response = api.preview("Bearer $token", request.toDto())
            val data = response.data
            if (!response.success || data == null) {
                throw ActivityPreparationServiceException(response.error?.message ?: "تعذر قراءة حالة النشاط.")
            }
            data.toDomain()
        }

    override suspend fun start(
        request: ActivityPreparationRequest,
        idempotencyKey: String,
    ): Result<ActivityStartResult> = runRequest { token ->
        val response = api.start("Bearer $token", idempotencyKey, request.toDto())
        val data = response.data
        if (!response.success || data == null) {
            throw ActivityPreparationServiceException(response.error?.message ?: "تعذر بدء النشاط.")
        }
        data.toDomain()
    }

    override suspend fun startStatus(idempotencyKey: String): Result<ActivityStartResult> =
        runRequest(statusLookup = true) { token ->
            val response = api.startStatus("Bearer $token", idempotencyKey)
            val data = response.data
            if (!response.success || data == null) {
                throw ActivityPreparationServiceException(response.error?.message ?: "تعذر التحقق من محاولة البدء.")
            }
            data.toDomain()
        }

    private suspend fun <T> runRequest(
        statusLookup: Boolean = false,
        block: suspend (String) -> T,
    ): Result<T> = runCatching {
        var tokens = sessionManager.readTokens() ?: throw ActivityPreparationSessionExpiredException()
        if (tokens.accessTokenNeedsRefresh(nowEpochSeconds())) tokens = refreshTokens(tokens.refreshToken)
        try {
            block(tokens.accessToken)
        } catch (error: HttpException) {
            if (error.code() != 401) throw error
            tokens = refreshTokens(tokens.refreshToken)
            block(tokens.accessToken)
        }
    }.recoverCatching { error ->
        if (error is CancellationException) throw error
        throw mapFailure(error, statusLookup)
    }

    private suspend fun refreshTokens(refreshToken: String): AuthTokens {
        try {
            val response = authApi.refresh(StudentRefreshRequestDto(refreshToken))
            val data = response.data ?: throw ActivityPreparationSessionExpiredException()
            if (!response.success) throw ActivityPreparationSessionExpiredException()
            return AuthTokens(
                accessToken = data.accessToken,
                refreshToken = data.refreshToken,
                expiresInSeconds = data.expiresIn,
                accessTokenExpiresAtEpochSeconds = nowEpochSeconds() + data.expiresIn,
            ).also { sessionManager.updateTokens(it) }
        } catch (error: HttpException) {
            if (error.code() in 400..403) throw ActivityPreparationSessionExpiredException(error)
            throw error
        }
    }

    private fun mapFailure(error: Throwable, statusLookup: Boolean): Throwable = when (error) {
        is ActivityPreparationException -> error
        is IOException -> ActivityPreparationNetworkException(error)
        is HttpException -> when (error.code()) {
            401, 403 -> ActivityPreparationSessionExpiredException(error)
            404 -> if (statusLookup) ActivityPreparationStartNotFoundException(error) else ActivityPreparationServiceException(cause = error)
            409 -> ActivityPreparationIdempotencyConflictException(error)
            429 -> ActivityPreparationServiceException("طلبات كثيرة خلال وقت قصير. انتظر قليلًا ثم حاول مجددًا.", error)
            else -> ActivityPreparationServiceException(cause = error)
        }
        else -> ActivityPreparationServiceException(cause = error)
    }
}

data class ActivityPreparationDependencies(
    val repository: ActivityPreparationRepository,
    val pendingStore: ActivityPreparationPendingStore,
)

object ActivityPreparationRepositoryFactory {
    fun create(
        sessionManager: SessionManager,
        baseUrl: String,
        dataStore: DataStore<Preferences>,
    ): ActivityPreparationDependencies = ActivityPreparationDependencies(
        repository = NetworkActivityPreparationRepository(
            api = MasaryNetwork.studentActivityPreparationApi(baseUrl),
            authApi = MasaryNetwork.studentAuthApi(baseUrl),
            sessionManager = sessionManager,
        ),
        pendingStore = DataStorePendingActivityStartStore(dataStore),
    )
}

private fun ActivityPreparationRequest.toDto(): ActivityPreparationRequestDto = ActivityPreparationRequestDto(
    subjectVersionId = subjectVersionId,
    unitId = unitId,
    lessonId = lessonId,
    activityType = activityType,
    activityMode = activityMode,
    guideStepId = guideStepId,
    source = source,
)

private fun ActivityPreparationPreviewDataDto.toDomain(): ActivityPreparationPreview = ActivityPreparationPreview(
    version = version,
    generatedAt = generatedAt,
    activity = ActivityDescriptor(
        subjectVersionId = activity.subjectVersionId,
        subjectName = activity.subjectName,
        unitId = activity.unitId,
        unitTitle = activity.unitTitle,
        lessonId = activity.lessonId,
        lessonTitle = activity.lessonTitle,
        activityType = activity.activityType,
        activityMode = activity.activityMode,
        title = activity.title,
        estimatedMinutes = activity.estimatedMinutes?.coerceAtLeast(0),
        questionCount = activity.questionCount?.coerceAtLeast(0),
    ),
    eligibility = ActivityEligibility(
        available = eligibility.available,
        status = eligibility.status,
        reason = eligibility.reason,
        reasonCode = eligibility.reasonCode,
    ),
    balances = ActivityBalances(balances.hearts.coerceAtLeast(0), balances.gems.coerceAtLeast(0)),
    cost = ActivityCost(
        cost.requiredHearts.coerceAtLeast(0),
        cost.heartCost.coerceAtLeast(0),
        cost.gemCost.coerceAtLeast(0),
    ),
    attempts = ActivityAttempts(
        available = attempts.available,
        used = attempts.used?.coerceAtLeast(0),
        remaining = attempts.remaining?.coerceAtLeast(0),
        maximum = attempts.maximum?.coerceAtLeast(0),
        reason = attempts.reason,
    ),
    resume = ActivityResume(
        available = resume.available,
        sessionId = resume.sessionId,
        status = resume.status,
        expiresAt = resume.expiresAt,
        reason = resume.reason,
    ),
)

private fun ActivityStartDataDto.toDomain(): ActivityStartResult = ActivityStartResult(
    sessionId = sessionId.also { require(it.isNotBlank()) },
    status = status,
    destination = destination,
    replayed = replayed,
    debit = ActivityDebit(debit.heartDebited.coerceAtLeast(0), debit.gemsDebited.coerceAtLeast(0)),
    balances = ActivityBalances(balances.hearts.coerceAtLeast(0), balances.gems.coerceAtLeast(0)),
    expiresAt = expiresAt,
)

sealed interface ActivityPreparationUiState {
    data object Loading : ActivityPreparationUiState
    data class Ready(val preview: ActivityPreparationPreview) : ActivityPreparationUiState
    data class Unavailable(val preview: ActivityPreparationPreview) : ActivityPreparationUiState
    data class Starting(val preview: ActivityPreparationPreview) : ActivityPreparationUiState
    data class StartUnknown(val preview: ActivityPreparationPreview?, val message: String) : ActivityPreparationUiState
    data class Started(val result: ActivityStartResult) : ActivityPreparationUiState
    data class Error(val message: String, val preview: ActivityPreparationPreview? = null) : ActivityPreparationUiState
    data object SessionExpired : ActivityPreparationUiState
}

class ActivityPreparationViewModel(
    private val repository: ActivityPreparationRepository,
    private val pendingStore: ActivityPreparationPendingStore,
    val request: ActivityPreparationRequest,
    private val now: () -> Long = System::currentTimeMillis,
    private val keyFactory: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {
    private val mutableState = MutableStateFlow<ActivityPreparationUiState>(ActivityPreparationUiState.Loading)
    val state: StateFlow<ActivityPreparationUiState> = mutableState.asStateFlow()

    init { viewModelScope.launch { restoreOrPreview() } }

    fun retryPreview() {
        if (mutableState.value is ActivityPreparationUiState.Starting) return
        viewModelScope.launch { loadPreview() }
    }

    fun start() {
        val preview = (mutableState.value as? ActivityPreparationUiState.Ready)?.preview ?: return
        mutableState.value = ActivityPreparationUiState.Starting(preview)
        viewModelScope.launch {
            val pending = pendingStore.read()?.takeIf { it.requestFingerprint == request.fingerprint }
                ?: PendingActivityStart(
                    idempotencyKey = keyFactory(),
                    requestFingerprint = request.fingerprint,
                    createdAtEpochMillis = now(),
                ).also { pendingStore.write(it) }
            applyStartResult(repository.start(request, pending.idempotencyKey), preview)
        }
    }

    fun resolveUnknownStart() {
        val current = mutableState.value as? ActivityPreparationUiState.StartUnknown ?: return
        viewModelScope.launch {
            val pending = pendingStore.read()?.takeIf { it.requestFingerprint == request.fingerprint }
            if (pending == null) {
                loadPreview()
                return@launch
            }
            val status = repository.startStatus(pending.idempotencyKey)
            if (status.exceptionOrNull() is ActivityPreparationStartNotFoundException) {
                applyStartResult(repository.start(request, pending.idempotencyKey), current.preview)
            } else {
                applyStartResult(status, current.preview)
            }
        }
    }

    fun confirmNavigation() {
        viewModelScope.launch { pendingStore.clear() }
    }

    private suspend fun restoreOrPreview() {
        val pending = pendingStore.read()
        if (pending == null || pending.requestFingerprint != request.fingerprint) {
            if (pending != null) pendingStore.clear()
            loadPreview()
            return
        }
        val result = repository.startStatus(pending.idempotencyKey)
        when (val error = result.exceptionOrNull()) {
            null -> mutableState.value = ActivityPreparationUiState.Started(result.getOrThrow())
            is ActivityPreparationStartNotFoundException -> loadPreview()
            is ActivityPreparationNetworkException -> mutableState.value =
                ActivityPreparationUiState.StartUnknown(null, error.message.orEmpty())
            is ActivityPreparationSessionExpiredException -> mutableState.value = ActivityPreparationUiState.SessionExpired
            else -> {
                pendingStore.clear()
                loadPreview()
            }
        }
    }

    private suspend fun loadPreview() {
        mutableState.value = ActivityPreparationUiState.Loading
        repository.preview(request).fold(
            onSuccess = { preview ->
                mutableState.value = if (preview.eligibility.available) {
                    ActivityPreparationUiState.Ready(preview)
                } else {
                    ActivityPreparationUiState.Unavailable(preview)
                }
            },
            onFailure = { error -> mutableState.value = failureState(error, null) },
        )
    }

    private suspend fun applyStartResult(
        result: Result<ActivityStartResult>,
        preview: ActivityPreparationPreview?,
    ) {
        result.fold(
            onSuccess = { mutableState.value = ActivityPreparationUiState.Started(it) },
            onFailure = { error ->
                mutableState.value = if (error is ActivityPreparationNetworkException) {
                    ActivityPreparationUiState.StartUnknown(preview, error.message.orEmpty())
                } else {
                    failureState(error, preview)
                }
            },
        )
    }

    private fun failureState(
        error: Throwable,
        preview: ActivityPreparationPreview?,
    ): ActivityPreparationUiState = when (error) {
        is CancellationException -> throw error
        is ActivityPreparationSessionExpiredException -> ActivityPreparationUiState.SessionExpired
        else -> ActivityPreparationUiState.Error(error.message ?: "تعذر تجهيز النشاط.", preview)
    }
}

private class ActivityPreparationViewModelFactory(
    private val repository: ActivityPreparationRepository,
    private val pendingStore: ActivityPreparationPendingStore,
    private val request: ActivityPreparationRequest,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(ActivityPreparationViewModel::class.java))
        return ActivityPreparationViewModel(repository, pendingStore, request) as T
    }
}

@Composable
fun ActivityPreparationRoute(
    destination: ActivityPreparationDestination,
    repository: ActivityPreparationRepository,
    pendingStore: ActivityPreparationPendingStore,
    onBack: () -> Unit,
    onSessionExpired: () -> Unit,
    onStarted: (ActivityStartResult) -> Unit,
) {
    val request = destination.toRequest()
    val model: ActivityPreparationViewModel = viewModel(
        key = "activity-preparation-${request.fingerprint.take(16)}",
        factory = ActivityPreparationViewModelFactory(repository, pendingStore, request),
    )
    val state by model.state.collectAsStateWithLifecycle()
    var deliveredSession by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(state) {
        when (val current = state) {
            ActivityPreparationUiState.SessionExpired -> onSessionExpired()
            is ActivityPreparationUiState.Started -> if (deliveredSession != current.result.sessionId) {
                deliveredSession = current.result.sessionId
                onStarted(current.result)
                model.confirmNavigation()
            }
            else -> Unit
        }
    }

    ActivityPreparationScreen(
        state = state,
        onBack = onBack,
        onStart = model::start,
        onRetry = model::retryPreview,
        onResolveUnknown = model::resolveUnknownStart,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActivityPreparationScreen(
    state: ActivityPreparationUiState,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onRetry: () -> Unit,
    onResolveUnknown: () -> Unit,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            containerColor = MasaryColors.background,
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.preparation_title), fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back))
                        }
                    },
                )
            },
        ) { paddingValues ->
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues).padding(20.dp),
                contentAlignment = Alignment.Center,
            ) {
                when (state) {
                    ActivityPreparationUiState.Loading,
                    ActivityPreparationUiState.SessionExpired,
                    is ActivityPreparationUiState.Started,
                    -> LoadingState()
                    is ActivityPreparationUiState.Ready -> PreviewState(state.preview, false, false, onStart)
                    is ActivityPreparationUiState.Unavailable -> PreviewState(state.preview, true, false, onStart)
                    is ActivityPreparationUiState.Starting -> PreviewState(state.preview, false, true, onStart)
                    is ActivityPreparationUiState.StartUnknown -> MessageState(
                        title = stringResource(R.string.start_unknown_title),
                        body = state.message,
                        action = stringResource(R.string.verify_start),
                        onAction = onResolveUnknown,
                    )
                    is ActivityPreparationUiState.Error -> MessageState(
                        title = stringResource(R.string.preparation_error_title),
                        body = state.message,
                        action = stringResource(R.string.retry),
                        onAction = onRetry,
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = MasaryColors.brandGold)
        Spacer(Modifier.height(14.dp))
        Text(stringResource(R.string.preparation_loading), color = MasaryColors.muted)
    }
}

@Composable
private fun PreviewState(
    preview: ActivityPreparationPreview,
    unavailable: Boolean,
    starting: Boolean,
    onStart: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(preview.activity.subjectName, color = MasaryColors.brandGold, fontWeight = FontWeight.Bold)
            Text(
                preview.activity.title.ifBlank { stringResource(R.string.default_activity_title) },
                style = MaterialTheme.typography.headlineSmall,
                color = MasaryColors.brandNavy,
                fontWeight = FontWeight.Bold,
            )
            val context = listOf(preview.activity.unitTitle, preview.activity.lessonTitle)
                .filter(String::isNotBlank)
                .joinToString(" — ")
            if (context.isNotBlank()) Text(context, color = MasaryColors.muted)
            HorizontalDivider()
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                preview.activity.estimatedMinutes?.let {
                    PreparationInfo(Icons.Outlined.Timer, stringResource(R.string.minutes_value, it))
                }
                PreparationInfo(Icons.Outlined.Favorite, stringResource(R.string.hearts_value, preview.balances.hearts))
            }
            preview.activity.questionCount?.let {
                Text(stringResource(R.string.questions_value, it), color = MasaryColors.muted)
            }
            if (preview.attempts.available) {
                Text(
                    stringResource(
                        R.string.attempts_value,
                        preview.attempts.used ?: 0,
                        preview.attempts.maximum ?: 0,
                    ),
                    color = MasaryColors.muted,
                )
            }
            if (preview.cost.heartCost > 0 || preview.cost.gemCost > 0) {
                AssistChip(
                    onClick = {},
                    label = {
                        Text(stringResource(R.string.cost_value, preview.cost.heartCost, preview.cost.gemCost))
                    },
                )
            }
            if (preview.resume.available) {
                Text(
                    stringResource(R.string.resume_available),
                    color = MasaryColors.brandNavy,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (unavailable) {
                Text(
                    preview.eligibility.reason.ifBlank { stringResource(R.string.activity_unavailable) },
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Button(
                onClick = onStart,
                enabled = !unavailable && !starting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (starting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                }
                Text(stringResource(if (preview.resume.available) R.string.resume_activity else R.string.start_activity))
            }
        }
    }
}

@Composable
private fun PreparationInfo(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MasaryColors.brandGold)
        Spacer(Modifier.width(6.dp))
        Text(text, color = MasaryColors.brandNavy)
    }
}

@Composable
private fun MessageState(
    title: String,
    body: String,
    action: String,
    onAction: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(body, color = MasaryColors.muted, textAlign = TextAlign.Center)
            Button(onClick = onAction, modifier = Modifier.fillMaxWidth()) { Text(action) }
        }
    }
}
