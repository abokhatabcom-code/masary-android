#!/usr/bin/env python3
from pathlib import Path
from textwrap import dedent

ROOT = Path(__file__).resolve().parents[1]


def write(path: str, content: str) -> None:
    target = ROOT / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(dedent(content).lstrip(), encoding="utf-8")


def replace_once(path: str, old: str, new: str) -> None:
    target = ROOT / path
    text = target.read_text(encoding="utf-8")
    if old not in text:
        raise SystemExit(f"Expected block not found in {path}: {old[:120]!r}")
    target.write_text(text.replace(old, new, 1), encoding="utf-8")


write(
    "core-network/src/main/java/app/masary/core/network/activity/StudentActivityPreparationApi.kt",
    r'''
    package app.masary.core.network.activity

    import com.google.gson.annotations.SerializedName
    import retrofit2.http.Body
    import retrofit2.http.GET
    import retrofit2.http.Header
    import retrofit2.http.POST
    import retrofit2.http.Query

    data class ActivityPreparationRequestDto(
        @SerializedName("subject_version_id") val subjectVersionId: Int,
        @SerializedName("unit_id") val unitId: Int? = null,
        @SerializedName("lesson_id") val lessonId: Int? = null,
        @SerializedName("activity_type") val activityType: String,
        @SerializedName("activity_mode") val activityMode: String,
        @SerializedName("guide_step_id") val guideStepId: Int? = null,
        val source: String,
    )

    data class ActivityPreparationErrorDto(
        val code: String = "",
        val message: String = "",
    )

    data class ActivityDescriptorDto(
        @SerializedName("subject_version_id") val subjectVersionId: Int = 0,
        @SerializedName("subject_name") val subjectName: String = "",
        @SerializedName("unit_id") val unitId: Int? = null,
        @SerializedName("unit_title") val unitTitle: String = "",
        @SerializedName("lesson_id") val lessonId: Int? = null,
        @SerializedName("lesson_title") val lessonTitle: String = "",
        @SerializedName("activity_type") val activityType: String = "",
        @SerializedName("activity_mode") val activityMode: String = "",
        val title: String = "",
        @SerializedName("estimated_minutes") val estimatedMinutes: Int? = null,
        @SerializedName("question_count") val questionCount: Int? = null,
    )

    data class ActivityEligibilityDto(
        val available: Boolean = false,
        val status: String = "unavailable",
        val reason: String = "",
        @SerializedName("reason_code") val reasonCode: String = "",
    )

    data class ActivityBalancesDto(
        val hearts: Int = 0,
        val gems: Int = 0,
    )

    data class ActivityCostDto(
        @SerializedName("required_hearts") val requiredHearts: Int = 0,
        @SerializedName("heart_cost") val heartCost: Int = 0,
        @SerializedName("gem_cost") val gemCost: Int = 0,
    )

    data class ActivityAttemptsDto(
        val available: Boolean = false,
        val used: Int? = null,
        val remaining: Int? = null,
        val maximum: Int? = null,
        val reason: String = "",
    )

    data class ActivityResumeDto(
        val available: Boolean = false,
        @SerializedName("session_id") val sessionId: String? = null,
        val status: String = "",
        @SerializedName("expires_at") val expiresAt: String = "",
        val reason: String = "",
    )

    data class ActivityPreparationPreviewDataDto(
        val version: String = "",
        @SerializedName("generated_at") val generatedAt: String = "",
        val activity: ActivityDescriptorDto = ActivityDescriptorDto(),
        val eligibility: ActivityEligibilityDto = ActivityEligibilityDto(),
        val balances: ActivityBalancesDto = ActivityBalancesDto(),
        val cost: ActivityCostDto = ActivityCostDto(),
        val attempts: ActivityAttemptsDto = ActivityAttemptsDto(),
        val resume: ActivityResumeDto = ActivityResumeDto(),
    )

    data class ActivityPreparationPreviewResponseDto(
        val success: Boolean,
        val data: ActivityPreparationPreviewDataDto? = null,
        val error: ActivityPreparationErrorDto? = null,
    )

    data class ActivityDebitDto(
        @SerializedName("heart_debited") val heartDebited: Int = 0,
        @SerializedName("gems_debited") val gemsDebited: Int = 0,
    )

    data class ActivityStartDataDto(
        @SerializedName("session_id") val sessionId: String = "",
        val status: String = "",
        val destination: String = "",
        val replayed: Boolean = false,
        val debit: ActivityDebitDto = ActivityDebitDto(),
        val balances: ActivityBalancesDto = ActivityBalancesDto(),
        @SerializedName("expires_at") val expiresAt: String = "",
    )

    data class ActivityStartResponseDto(
        val success: Boolean,
        val data: ActivityStartDataDto? = null,
        val error: ActivityPreparationErrorDto? = null,
    )

    interface StudentActivityPreparationApi {
        @POST("/api/v1/student/activity/preview")
        suspend fun preview(
            @Header("Authorization") authorization: String,
            @Body request: ActivityPreparationRequestDto,
        ): ActivityPreparationPreviewResponseDto

        @POST("/api/v1/student/activity/start")
        suspend fun start(
            @Header("Authorization") authorization: String,
            @Header("Idempotency-Key") idempotencyKey: String,
            @Body request: ActivityPreparationRequestDto,
        ): ActivityStartResponseDto

        @GET("/api/v1/student/activity/start-status")
        suspend fun startStatus(
            @Header("Authorization") authorization: String,
            @Query("idempotency_key") idempotencyKey: String,
        ): ActivityStartResponseDto
    }
    ''',
)

write(
    "feature-activity-preparation/build.gradle.kts",
    r'''
    plugins {
        alias(libs.plugins.android.library)
        alias(libs.plugins.kotlin.android)
        alias(libs.plugins.kotlin.compose)
        alias(libs.plugins.kotlin.serialization)
    }

    android {
        namespace = "app.masary.feature.activitypreparation"
        compileSdk = 35
        defaultConfig { minSdk = 26 }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
        kotlinOptions { jvmTarget = "17" }
        buildFeatures { compose = true }
    }

    dependencies {
        implementation(project(":core-models"))
        implementation(project(":core-ui"))
        implementation(project(":core-network"))
        implementation(project(":core-datastore"))
        implementation(libs.androidx.core.ktx)
        implementation(libs.androidx.lifecycle.viewmodel.ktx)
        implementation(libs.androidx.lifecycle.viewmodel.compose)
        implementation(libs.androidx.lifecycle.runtime.compose)
        implementation(libs.androidx.datastore.preferences)
        implementation(libs.kotlinx.coroutines.core)
        implementation(libs.kotlinx.coroutines.android)
        implementation(libs.retrofit.core)
        implementation(libs.kotlinx.serialization.json)
        implementation(platform(libs.androidx.compose.bom))
        implementation(libs.androidx.compose.ui)
        implementation(libs.androidx.compose.ui.tooling.preview)
        implementation(libs.androidx.compose.material3)
        implementation(libs.androidx.compose.material.icons.extended)
        debugImplementation(libs.androidx.compose.ui.tooling)
        testImplementation(libs.junit)
        testImplementation(libs.kotlinx.coroutines.test)
    }
    ''',
)

write(
    "feature-activity-preparation/src/main/AndroidManifest.xml",
    r'''
    <manifest xmlns:android="http://schemas.android.com/apk/res/android" />
    ''',
)

write(
    "feature-activity-preparation/src/main/java/app/masary/feature/activitypreparation/domain/ActivityPreparationModels.kt",
    r'''
    package app.masary.feature.activitypreparation.domain

    import java.security.MessageDigest

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
                    .joinToString("") { "%02x".format(it) }
            }

        companion object {
            val SUPPORTED_TYPES = setOf(
                "guide_step", "unit_test", "lesson_practice", "review", "smart_review", "speed_test",
            )
            val SUPPORTED_MODES = setOf("learn", "practice", "review", "test", "speed")
            val SUPPORTED_SOURCES = setOf("home_guide", "guide", "subject", "unit", "lesson", "review")

            fun fromGuide(
                subjectVersionId: Int,
                unitId: Int?,
                actionKey: String,
                guideStepId: Int?,
            ): ActivityPreparationRequest {
                val key = actionKey.trim().lowercase()
                val type = when {
                    "speed" in key -> "speed_test"
                    "mistake" in key || "error" in key || "review" in key -> "review"
                    "test" in key || "quiz" in key || "attempt" in key -> "unit_test"
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
    class ActivityPreparationNetworkException(cause: Throwable? = null) : ActivityPreparationException("تعذر الاتصال بالخادم. تحقق من الإنترنت ثم حاول مجددًا.", cause)
    class ActivityPreparationServiceException(message: String = "تعذر تجهيز النشاط الآن.", cause: Throwable? = null) : ActivityPreparationException(message, cause)
    class ActivityPreparationSessionExpiredException(cause: Throwable? = null) : ActivityPreparationException("انتهت جلسة الدخول.", cause)
    class ActivityPreparationIdempotencyConflictException(cause: Throwable? = null) : ActivityPreparationException("تعارضت محاولة البدء السابقة. أعد فتح النشاط.", cause)
    class ActivityPreparationStartNotFoundException(cause: Throwable? = null) : ActivityPreparationException("لم تُسجّل محاولة البدء بعد.", cause)
    ''',
)

write(
    "feature-activity-preparation/src/main/java/app/masary/feature/activitypreparation/data/DataStorePendingActivityStartStore.kt",
    r'''
    package app.masary.feature.activitypreparation.data

    import androidx.datastore.core.DataStore
    import androidx.datastore.preferences.core.Preferences
    import androidx.datastore.preferences.core.edit
    import androidx.datastore.preferences.core.longPreferencesKey
    import androidx.datastore.preferences.core.stringPreferencesKey
    import app.masary.feature.activitypreparation.domain.ActivityPreparationPendingStore
    import app.masary.feature.activitypreparation.domain.PendingActivityStart
    import kotlinx.coroutines.flow.first

    class DataStorePendingActivityStartStore(
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
            dataStore.edit {
                it[KEY] = value.idempotencyKey
                it[FINGERPRINT] = value.requestFingerprint
                it[CREATED_AT] = value.createdAtEpochMillis
            }
        }

        override suspend fun clear() {
            dataStore.edit {
                it.remove(KEY)
                it.remove(FINGERPRINT)
                it.remove(CREATED_AT)
            }
        }

        private companion object {
            val KEY = stringPreferencesKey("activity_start_key")
            val FINGERPRINT = stringPreferencesKey("activity_start_fingerprint")
            val CREATED_AT = longPreferencesKey("activity_start_created_at")
            const val MAX_AGE_MS = 6 * 60 * 60 * 1000L
        }
    }
    ''',
)

write(
    "feature-activity-preparation/src/main/java/app/masary/feature/activitypreparation/data/NetworkActivityPreparationRepository.kt",
    r'''
    package app.masary.feature.activitypreparation.data

    import app.masary.core.datastore.SessionManager
    import app.masary.core.models.auth.AuthTokens
    import app.masary.core.network.activity.ActivityPreparationRequestDto
    import app.masary.core.network.activity.ActivityPreparationPreviewDataDto
    import app.masary.core.network.activity.ActivityStartDataDto
    import app.masary.core.network.activity.StudentActivityPreparationApi
    import app.masary.core.network.auth.StudentAuthApi
    import app.masary.core.network.auth.StudentRefreshRequestDto
    import app.masary.feature.activitypreparation.domain.*
    import java.io.IOException
    import kotlinx.coroutines.CancellationException
    import retrofit2.HttpException

    class NetworkActivityPreparationRepository(
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

    private fun ActivityPreparationRequest.toDto() = ActivityPreparationRequestDto(
        subjectVersionId = subjectVersionId,
        unitId = unitId,
        lessonId = lessonId,
        activityType = activityType,
        activityMode = activityMode,
        guideStepId = guideStepId,
        source = source,
    )

    private fun ActivityPreparationPreviewDataDto.toDomain() = ActivityPreparationPreview(
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
            eligibility.available,
            eligibility.status,
            eligibility.reason,
            eligibility.reasonCode,
        ),
        balances = ActivityBalances(balances.hearts.coerceAtLeast(0), balances.gems.coerceAtLeast(0)),
        cost = ActivityCost(cost.requiredHearts.coerceAtLeast(0), cost.heartCost.coerceAtLeast(0), cost.gemCost.coerceAtLeast(0)),
        attempts = ActivityAttempts(
            attempts.available,
            attempts.used?.coerceAtLeast(0),
            attempts.remaining?.coerceAtLeast(0),
            attempts.maximum?.coerceAtLeast(0),
            attempts.reason,
        ),
        resume = ActivityResume(resume.available, resume.sessionId, resume.status, resume.expiresAt, resume.reason),
    )

    private fun ActivityStartDataDto.toDomain() = ActivityStartResult(
        sessionId = sessionId.also { require(it.isNotBlank()) },
        status = status,
        destination = destination,
        replayed = replayed,
        debit = ActivityDebit(debit.heartDebited.coerceAtLeast(0), debit.gemsDebited.coerceAtLeast(0)),
        balances = ActivityBalances(balances.hearts.coerceAtLeast(0), balances.gems.coerceAtLeast(0)),
        expiresAt = expiresAt,
    )
    ''',
)

write(
    "feature-activity-preparation/src/main/java/app/masary/feature/activitypreparation/data/ActivityPreparationRepositoryFactory.kt",
    r'''
    package app.masary.feature.activitypreparation.data

    import androidx.datastore.core.DataStore
    import androidx.datastore.preferences.core.Preferences
    import app.masary.core.datastore.SessionManager
    import app.masary.core.network.MasaryNetwork
    import app.masary.feature.activitypreparation.domain.ActivityPreparationPendingStore
    import app.masary.feature.activitypreparation.domain.ActivityPreparationRepository

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
    ''',
)

write(
    "feature-activity-preparation/src/main/java/app/masary/feature/activitypreparation/ui/ActivityPreparationDestination.kt",
    r'''
    package app.masary.feature.activitypreparation.ui

    import app.masary.feature.activitypreparation.domain.ActivityPreparationRequest
    import kotlinx.serialization.Serializable

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
        fun toRequest() = ActivityPreparationRequest(
            subjectVersionId,
            unitId,
            lessonId,
            activityType,
            activityMode,
            guideStepId,
            source,
        )

        companion object {
            fun fromGuide(
                subjectVersionId: Int,
                unitId: Int?,
                actionKey: String,
                guideStepId: Int?,
            ): ActivityPreparationDestination {
                val request = ActivityPreparationRequest.fromGuide(subjectVersionId, unitId, actionKey, guideStepId)
                return ActivityPreparationDestination(
                    request.subjectVersionId,
                    request.unitId,
                    request.lessonId,
                    request.activityType,
                    request.activityMode,
                    request.guideStepId,
                    request.source,
                )
            }
        }
    }
    ''',
)

write(
    "feature-activity-preparation/src/main/java/app/masary/feature/activitypreparation/ui/ActivityPreparationViewModel.kt",
    r'''
    package app.masary.feature.activitypreparation.ui

    import androidx.lifecycle.ViewModel
    import androidx.lifecycle.ViewModelProvider
    import androidx.lifecycle.viewModelScope
    import app.masary.feature.activitypreparation.domain.*
    import java.util.UUID
    import kotlinx.coroutines.CancellationException
    import kotlinx.coroutines.flow.MutableStateFlow
    import kotlinx.coroutines.flow.StateFlow
    import kotlinx.coroutines.flow.asStateFlow
    import kotlinx.coroutines.launch

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
                    ?: PendingActivityStart(keyFactory(), request.fingerprint, now()).also { pendingStore.write(it) }
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
                val error = status.exceptionOrNull()
                if (error is ActivityPreparationStartNotFoundException) {
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
            val error = result.exceptionOrNull()
            when (error) {
                null -> mutableState.value = ActivityPreparationUiState.Started(result.getOrThrow())
                is ActivityPreparationStartNotFoundException -> loadPreview()
                is ActivityPreparationNetworkException -> mutableState.value = ActivityPreparationUiState.StartUnknown(null, error.message.orEmpty())
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
                onSuccess = {
                    mutableState.value = if (it.eligibility.available) {
                        ActivityPreparationUiState.Ready(it)
                    } else {
                        ActivityPreparationUiState.Unavailable(it)
                    }
                },
                onFailure = { mutableState.value = failureState(it, null) },
            )
        }

        private suspend fun applyStartResult(
            result: Result<ActivityStartResult>,
            preview: ActivityPreparationPreview?,
        ) {
            result.fold(
                onSuccess = { mutableState.value = ActivityPreparationUiState.Started(it) },
                onFailure = {
                    mutableState.value = when (it) {
                        is ActivityPreparationNetworkException -> ActivityPreparationUiState.StartUnknown(preview, it.message.orEmpty())
                        else -> failureState(it, preview)
                    }
                },
            )
        }

        private fun failureState(error: Throwable, preview: ActivityPreparationPreview?): ActivityPreparationUiState = when (error) {
            is CancellationException -> throw error
            is ActivityPreparationSessionExpiredException -> ActivityPreparationUiState.SessionExpired
            else -> ActivityPreparationUiState.Error(error.message ?: "تعذر تجهيز النشاط.", preview)
        }
    }

    class ActivityPreparationViewModelFactory(
        private val repository: ActivityPreparationRepository,
        private val pendingStore: ActivityPreparationPendingStore,
        private val request: ActivityPreparationRequest,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ActivityPreparationViewModel(repository, pendingStore, request) as T
    }
    ''',
)

write(
    "feature-activity-preparation/src/main/java/app/masary/feature/activitypreparation/ui/ActivityPreparationScreen.kt",
    r'''
    package app.masary.feature.activitypreparation.ui

    import androidx.compose.foundation.layout.*
    import androidx.compose.material.icons.Icons
    import androidx.compose.material.icons.automirrored.outlined.ArrowBack
    import androidx.compose.material.icons.outlined.Favorite
    import androidx.compose.material.icons.outlined.Timer
    import androidx.compose.material3.*
    import androidx.compose.runtime.*
    import androidx.compose.runtime.saveable.rememberSaveable
    import androidx.compose.ui.Alignment
    import androidx.compose.ui.Modifier
    import androidx.compose.ui.platform.LocalLayoutDirection
    import androidx.compose.ui.res.stringResource
    import androidx.compose.ui.text.font.FontWeight
    import androidx.compose.ui.text.style.TextAlign
    import androidx.compose.ui.unit.LayoutDirection
    import androidx.compose.ui.unit.dp
    import androidx.lifecycle.compose.collectAsStateWithLifecycle
    import androidx.lifecycle.viewmodel.compose.viewModel
    import app.masary.core.ui.MasaryColors
    import app.masary.feature.activitypreparation.R
    import app.masary.feature.activitypreparation.domain.*

    @Composable
    fun ActivityPreparationRoute(
        destination: ActivityPreparationDestination,
        repository: ActivityPreparationRepository,
        pendingStore: ActivityPreparationPendingStore,
        onBack: () -> Unit,
        onSessionExpired: () -> Unit,
        onStarted: (ActivityStartResult) -> Unit,
    ) {
        val request = remember(destination) { destination.toRequest() }
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
            ) { padding ->
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    when (state) {
                        ActivityPreparationUiState.Loading,
                        ActivityPreparationUiState.SessionExpired,
                        -> LoadingState()
                        is ActivityPreparationUiState.Ready -> PreviewState(state.preview, false, onStart)
                        is ActivityPreparationUiState.Unavailable -> PreviewState(state.preview, true, onStart)
                        is ActivityPreparationUiState.Starting -> PreviewState(state.preview, false, onStart, starting = true)
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
                        is ActivityPreparationUiState.Started -> LoadingState()
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
        onStart: () -> Unit,
        starting: Boolean = false,
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
                val context = listOf(preview.activity.unitTitle, preview.activity.lessonTitle).filter(String::isNotBlank).joinToString(" — ")
                if (context.isNotBlank()) Text(context, color = MasaryColors.muted)
                HorizontalDivider()
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    preview.activity.estimatedMinutes?.let {
                        Info(Icons.Outlined.Timer, stringResource(R.string.minutes_value, it))
                    }
                    Info(Icons.Outlined.Favorite, stringResource(R.string.hearts_value, preview.balances.hearts))
                }
                preview.activity.questionCount?.let { Text(stringResource(R.string.questions_value, it), color = MasaryColors.muted) }
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
                        label = { Text(stringResource(R.string.cost_value, preview.cost.heartCost, preview.cost.gemCost)) },
                    )
                }
                if (preview.resume.available) {
                    Text(stringResource(R.string.resume_available), color = MasaryColors.brandNavy, fontWeight = FontWeight.Bold)
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
    private fun Info(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MasaryColors.brandGold)
            Spacer(Modifier.width(6.dp))
            Text(text, color = MasaryColors.brandNavy)
        }
    }

    @Composable
    private fun MessageState(title: String, body: String, action: String, onAction: () -> Unit) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text(body, color = MasaryColors.muted, textAlign = TextAlign.Center)
                Button(onClick = onAction, modifier = Modifier.fillMaxWidth()) { Text(action) }
            }
        }
    }
    ''',
)

write(
    "feature-activity-preparation/src/main/res/values/strings.xml",
    r'''
    <resources>
        <string name="preparation_title">تجهيز النشاط</string>
        <string name="preparation_loading">نتحقق من جاهزية النشاط…</string>
        <string name="default_activity_title">نشاط تعليمي</string>
        <string name="start_activity">ابدأ النشاط</string>
        <string name="resume_activity">استئناف النشاط</string>
        <string name="resume_available">لديك جلسة سابقة قابلة للاستئناف.</string>
        <string name="activity_unavailable">هذا النشاط غير متاح الآن.</string>
        <string name="preparation_error_title">تعذر تجهيز النشاط</string>
        <string name="start_unknown_title">نتيجة البدء غير مؤكدة</string>
        <string name="verify_start">تحقق من حالة البدء</string>
        <string name="retry">إعادة المحاولة</string>
        <string name="back">رجوع</string>
        <string name="minutes_value">%1$d دقيقة</string>
        <string name="hearts_value">%1$d قلوب</string>
        <string name="questions_value">%1$d سؤالًا</string>
        <string name="attempts_value">المحاولات: %1$d من %2$d</string>
        <string name="cost_value">التكلفة: %1$d قلب، %2$d جوهرة</string>
    </resources>
    ''',
)

write(
    "feature-activity-preparation/src/test/java/app/masary/feature/activitypreparation/domain/ActivityPreparationRequestTest.kt",
    r'''
    package app.masary.feature.activitypreparation.domain

    import org.junit.Assert.assertEquals
    import org.junit.Assert.assertNotEquals
    import org.junit.Assert.assertThrows
    import org.junit.Test

    class ActivityPreparationRequestTest {
        @Test fun `fingerprint is stable and request scoped`() {
            val first = ActivityPreparationRequest.fromGuide(12, 4, "review_mistakes", 91)
            val second = ActivityPreparationRequest.fromGuide(12, 4, "review_mistakes", 91)
            assertEquals(first.fingerprint, second.fingerprint)
            assertNotEquals(first.fingerprint, second.copy(unitId = 5).fingerprint)
        }

        @Test fun `invalid identifiers are rejected`() {
            assertThrows(IllegalArgumentException::class.java) {
                ActivityPreparationRequest(0, activityType = "guide_step", activityMode = "learn", source = "guide")
            }
        }
    }
    ''',
)

write(
    "feature-activity-preparation/src/test/java/app/masary/feature/activitypreparation/ui/ActivityPreparationViewModelTest.kt",
    r'''
    package app.masary.feature.activitypreparation.ui

    import app.masary.feature.activitypreparation.domain.*
    import kotlinx.coroutines.Dispatchers
    import kotlinx.coroutines.ExperimentalCoroutinesApi
    import kotlinx.coroutines.test.UnconfinedTestDispatcher
    import kotlinx.coroutines.test.advanceUntilIdle
    import kotlinx.coroutines.test.resetMain
    import kotlinx.coroutines.test.runTest
    import kotlinx.coroutines.test.setMain
    import org.junit.After
    import org.junit.Assert.assertEquals
    import org.junit.Assert.assertTrue
    import org.junit.Before
    import org.junit.Test

    @OptIn(ExperimentalCoroutinesApi::class)
    class ActivityPreparationViewModelTest {
        private val dispatcher = UnconfinedTestDispatcher()
        @Before fun before() = Dispatchers.setMain(dispatcher)
        @After fun after() = Dispatchers.resetMain()

        @Test fun `double start creates one request and keeps the same key`() = runTest {
            val repository = FakeRepository()
            val store = MemoryStore()
            val request = ActivityPreparationRequest.fromGuide(12, 4, "learn", 9)
            val model = ActivityPreparationViewModel(repository, store, request, keyFactory = { "12345678-1234-1234-1234-123456789012" })
            advanceUntilIdle()
            model.start()
            model.start()
            advanceUntilIdle()
            assertEquals(1, repository.startCalls)
            assertTrue(model.state.value is ActivityPreparationUiState.Started)
            assertEquals("12345678-1234-1234-1234-123456789012", repository.lastKey)
        }

        private class MemoryStore : ActivityPreparationPendingStore {
            var value: PendingActivityStart? = null
            override suspend fun read() = value
            override suspend fun write(value: PendingActivityStart) { this.value = value }
            override suspend fun clear() { value = null }
        }

        private class FakeRepository : ActivityPreparationRepository {
            var startCalls = 0
            var lastKey = ""
            override suspend fun preview(request: ActivityPreparationRequest) = Result.success(preview())
            override suspend fun start(request: ActivityPreparationRequest, idempotencyKey: String): Result<ActivityStartResult> {
                startCalls += 1
                lastKey = idempotencyKey
                return Result.success(startResult())
            }
            override suspend fun startStatus(idempotencyKey: String) = Result.failure<ActivityStartResult>(ActivityPreparationStartNotFoundException())
            private fun preview() = ActivityPreparationPreview(
                "v1", "now",
                ActivityDescriptor(12, "الرياضيات", 4, "الوحدة", null, "", "guide_step", "learn", "ابدأ", 5, null),
                ActivityEligibility(true, "ready", "", ""),
                ActivityBalances(3, 10), ActivityCost(0, 0, 0),
                ActivityAttempts(false, null, null, null, ""), ActivityResume(false, null, "", "", ""),
            )
            private fun startResult() = ActivityStartResult(
                "session-1", "created", "activity_session_pending_ui", false,
                ActivityDebit(0, 0), ActivityBalances(3, 10), "later",
            )
        }
    }
    ''',
)

write(
    "feature-home/src/main/java/app/masary/feature/home/ui/ActivitySessionReadyScreen.kt",
    r'''
    package app.masary.feature.home.ui

    import androidx.compose.foundation.layout.*
    import androidx.compose.material.icons.Icons
    import androidx.compose.material.icons.outlined.CheckCircle
    import androidx.compose.material3.*
    import androidx.compose.runtime.Composable
    import androidx.compose.ui.Alignment
    import androidx.compose.ui.Modifier
    import androidx.compose.ui.text.font.FontWeight
    import androidx.compose.ui.text.style.TextAlign
    import androidx.compose.ui.unit.dp
    import app.masary.core.ui.MasaryColors
    import kotlinx.serialization.Serializable

    @Serializable
    internal data class ActivitySessionDestination(
        val sessionId: String,
        val destination: String,
        val expiresAt: String,
    )

    @Composable
    internal fun ActivitySessionReadyScreen(destination: ActivitySessionDestination, onBack: () -> Unit) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(
                    Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Icon(Icons.Outlined.CheckCircle, null, tint = MasaryColors.brandGold, modifier = Modifier.size(72.dp))
                    Text("تم تجهيز النشاط", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MasaryColors.brandNavy)
                    Text(
                        "أُنشئت جلسة آمنة بنجاح. ستُربط واجهة الأسئلة بهذه الجلسة في المرحلة المختصة.",
                        color = MasaryColors.muted,
                        textAlign = TextAlign.Center,
                    )
                    Text("رقم الجلسة: ${destination.sessionId.take(12)}…", color = MasaryColors.muted)
                    Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("العودة إلى المواد") }
                }
            }
        }
    }
    ''',
)

replace_once(
    "settings.gradle.kts",
    '    ":feature-home",\n    ":feature-notifications",\n',
    '    ":feature-home",\n    ":feature-activity-preparation",\n    ":feature-notifications",\n',
)

replace_once(
    "feature-home/build.gradle.kts",
    '    implementation(project(":feature-notifications"))\n',
    '    implementation(project(":feature-notifications"))\n    implementation(project(":feature-activity-preparation"))\n',
)

replace_once(
    "app-student/build.gradle.kts",
    '    implementation(project(":feature-home"))\n',
    '    implementation(project(":feature-home"))\n    implementation(project(":feature-activity-preparation"))\n',
)
replace_once(
    "app-student/build.gradle.kts",
    '        ":feature-home:testDebugUnitTest",\n',
    '        ":feature-home:testDebugUnitTest",\n        ":feature-activity-preparation:testDebugUnitTest",\n',
)

replace_once(
    "core-network/src/main/java/app/masary/core/network/MasaryNetwork.kt",
    'import app.masary.core.network.auth.StudentAuthApi\n',
    'import app.masary.core.network.activity.StudentActivityPreparationApi\nimport app.masary.core.network.auth.StudentAuthApi\n',
)
replace_once(
    "core-network/src/main/java/app/masary/core/network/MasaryNetwork.kt",
    '    fun studentPushTokenApi(baseUrl: String, client: OkHttpClient = defaultClient()): StudentPushTokenApi =\n        retrofit(validateBaseUrl(baseUrl), client).create(StudentPushTokenApi::class.java)\n\n',
    '    fun studentActivityPreparationApi(\n        baseUrl: String,\n        client: OkHttpClient = defaultClient(),\n    ): StudentActivityPreparationApi = retrofit(validateBaseUrl(baseUrl), client)\n        .create(StudentActivityPreparationApi::class.java)\n\n    fun studentPushTokenApi(baseUrl: String, client: OkHttpClient = defaultClient()): StudentPushTokenApi =\n        retrofit(validateBaseUrl(baseUrl), client).create(StudentPushTokenApi::class.java)\n\n',
)

replace_once(
    "app-student/src/main/java/app/masary/student/MainActivity.kt",
    'import app.masary.feature.auth.data.AuthRepositoryFactory\n',
    'import app.masary.feature.activitypreparation.data.ActivityPreparationRepositoryFactory\nimport app.masary.feature.auth.data.AuthRepositoryFactory\n',
)
replace_once(
    "app-student/src/main/java/app/masary/student/MainActivity.kt",
    'private val ComponentActivity.homeSnapshotDataStore by preferencesDataStore(name = "student_home_snapshot")\n',
    'private val ComponentActivity.homeSnapshotDataStore by preferencesDataStore(name = "student_home_snapshot")\nprivate val ComponentActivity.activityPreparationDataStore by preferencesDataStore(name = "student_activity_preparation")\n',
)
replace_once(
    "app-student/src/main/java/app/masary/student/MainActivity.kt",
    '        val homeRepository = HomeRepositoryFactory.create(sessionManager, BuildConfig.MASARY_API_BASE_URL, homeSnapshotDataStore)\n',
    '        val homeRepository = HomeRepositoryFactory.create(sessionManager, BuildConfig.MASARY_API_BASE_URL, homeSnapshotDataStore)\n        val activityPreparation = ActivityPreparationRepositoryFactory.create(\n            sessionManager,\n            BuildConfig.MASARY_API_BASE_URL,\n            activityPreparationDataStore,\n        )\n',
)
replace_once(
    "app-student/src/main/java/app/masary/student/MainActivity.kt",
    '                    homeRepository = homeRepository,\n',
    '                    homeRepository = homeRepository,\n                    activityPreparationRepository = activityPreparation.repository,\n                    activityPreparationPendingStore = activityPreparation.pendingStore,\n',
)

replace_once(
    "app-student/src/main/java/app/masary/student/MasaryStudentApp.kt",
    'import app.masary.feature.auth.domain.AuthRepository\n',
    'import app.masary.feature.activitypreparation.domain.ActivityPreparationPendingStore\nimport app.masary.feature.activitypreparation.domain.ActivityPreparationRepository\nimport app.masary.feature.auth.domain.AuthRepository\n',
)
replace_once(
    "app-student/src/main/java/app/masary/student/MasaryStudentApp.kt",
    '    homeRepository: HomeRepository,\n    deviceName: String,\n',
    '    homeRepository: HomeRepository,\n    activityPreparationRepository: ActivityPreparationRepository,\n    activityPreparationPendingStore: ActivityPreparationPendingStore,\n    deviceName: String,\n',
)
replace_once(
    "app-student/src/main/java/app/masary/student/MasaryStudentApp.kt",
    '                    repository = homeRepository,\n                    onLogout = {\n',
    '                    repository = homeRepository,\n                    activityPreparationRepository = activityPreparationRepository,\n                    activityPreparationPendingStore = activityPreparationPendingStore,\n                    onLogout = {\n',
)

screen = "feature-home/src/main/java/app/masary/feature/home/ui/StudentHomeScreen.kt"
replace_once(
    screen,
    'import app.masary.feature.home.R\n',
    'import app.masary.feature.activitypreparation.domain.ActivityPreparationPendingStore\nimport app.masary.feature.activitypreparation.domain.ActivityPreparationRepository\nimport app.masary.feature.activitypreparation.ui.ActivityPreparationDestination\nimport app.masary.feature.activitypreparation.ui.ActivityPreparationRoute\nimport app.masary.feature.home.R\n',
)
replace_once(
    screen,
    '    repository: HomeRepository,\n    onLogout: () -> Unit,\n',
    '    repository: HomeRepository,\n    activityPreparationRepository: ActivityPreparationRepository,\n    activityPreparationPendingStore: ActivityPreparationPendingStore,\n    onLogout: () -> Unit,\n',
)
replace_once(
    screen,
    '    val selectedDestination = when {\n        backStack?.destination?.hasRoute<StudentDestination.Guide>() == true -> StudentDestination.Guide\n',
    '    val selectedDestination = when {\n        backStack?.destination?.hasRoute<ActivityPreparationDestination>() == true ||\n            backStack?.destination?.hasRoute<ActivitySessionDestination>() == true -> StudentDestination.Subjects\n        backStack?.destination?.hasRoute<StudentDestination.Guide>() == true -> StudentDestination.Guide\n',
)
replace_once(
    screen,
    '    fun navigateTo(destination: StudentDestination) {\n        navController.navigate(destination) {\n            launchSingleTop = true\n            popUpTo(StudentDestination.Home) { saveState = true }\n            restoreState = true\n        }\n    }\n\n',
    '    fun navigateTo(destination: StudentDestination) {\n        navController.navigate(destination) {\n            launchSingleTop = true\n            popUpTo(StudentDestination.Home) { saveState = true }\n            restoreState = true\n        }\n    }\n\n    fun navigateToPreparation(step: HomeSmartGuideStep) {\n        navController.navigate(\n            ActivityPreparationDestination.fromGuide(\n                subjectVersionId = step.subjectVersionId,\n                unitId = step.unitId,\n                actionKey = step.actionKey,\n                guideStepId = step.id.takeIf { it > 0 },\n            ),\n        ) { launchSingleTop = true }\n    }\n\n',
)
replace_once(
    screen,
    '                        onGuideStep = { step ->\n                            navigateTo(\n                                StudentDestination.SubjectDetails(\n                                    subjectVersionId = step.subjectVersionId,\n                                    unitId = step.unitId,\n                                    actionKey = step.actionKey,\n                                ),\n                            )\n                        },\n',
    '                        onGuideStep = ::navigateToPreparation,\n',
)
replace_once(
    screen,
    '                            onStep = { step ->\n                                navigateTo(\n                                    StudentDestination.SubjectDetails(\n                                        subjectVersionId = step.subjectVersionId,\n                                        unitId = step.unitId,\n                                        actionKey = step.actionKey,\n                                    ),\n                                )\n                            },\n',
    '                            onStep = ::navigateToPreparation,\n',
)
replace_once(
    screen,
    '                composable<StudentDestination.SubjectDetails> { entry ->\n                    val destination = entry.toRoute<StudentDestination.SubjectDetails>()\n                    DataDestination(currentData) { data ->\n                        SubjectDetailsSection(data, destination)\n                    }\n                }\n            }\n',
    '                composable<StudentDestination.SubjectDetails> { entry ->\n                    val destination = entry.toRoute<StudentDestination.SubjectDetails>()\n                    DataDestination(currentData) { data ->\n                        SubjectDetailsSection(data, destination)\n                    }\n                }\n                composable<ActivityPreparationDestination> { entry ->\n                    ActivityPreparationRoute(\n                        destination = entry.toRoute(),\n                        repository = activityPreparationRepository,\n                        pendingStore = activityPreparationPendingStore,\n                        onBack = { navController.popBackStack() },\n                        onSessionExpired = onLogout,\n                        onStarted = { result ->\n                            navController.popBackStack()\n                            navController.navigate(\n                                ActivitySessionDestination(result.sessionId, result.destination, result.expiresAt),\n                            ) { launchSingleTop = true }\n                        },\n                    )\n                }\n                composable<ActivitySessionDestination> { entry ->\n                    ActivitySessionReadyScreen(entry.toRoute()) {\n                        navController.navigate(StudentDestination.Subjects) {\n                            popUpTo(StudentDestination.Home) { saveState = true }\n                            launchSingleTop = true\n                            restoreState = true\n                        }\n                    }\n                }\n            }\n',
)

replace_once(
    "core-network/src/test/java/app/masary/core/network/ApiContractFixtureTest.kt",
    'import app.masary.core.network.auth.StudentLoginResponseDto\n',
    'import app.masary.core.network.activity.ActivityPreparationPreviewResponseDto\nimport app.masary.core.network.activity.ActivityStartResponseDto\nimport app.masary.core.network.auth.StudentLoginResponseDto\n',
)
replace_once(
    "core-network/src/test/java/app/masary/core/network/ApiContractFixtureTest.kt",
    '        assertEquals("news", home.data?.spotlight?.type)\n',
    '        assertEquals("news", home.data?.spotlight?.type)\n\n        val preview = parse("activity-preview-success.json", ActivityPreparationPreviewResponseDto::class.java)\n        assertTrue(preview.data?.eligibility?.available == true)\n        assertEquals(3, preview.data?.balances?.hearts)\n        val start = parse("activity-start-success.json", ActivityStartResponseDto::class.java)\n        assertEquals("activity-session-001", start.data?.sessionId)\n',
)
replace_once(
    "core-network/src/test/java/app/masary/core/network/ApiContractFixtureTest.kt",
    '            gson.fromJson(json, StudentHomeResponseDto::class.java),\n',
    '            gson.fromJson(json, StudentHomeResponseDto::class.java),\n            gson.fromJson(json, ActivityPreparationPreviewResponseDto::class.java),\n            gson.fromJson(json, ActivityStartResponseDto::class.java),\n',
)

print("Phase 08 Android implementation applied.")
