package app.masary.feature.questionsession.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.masary.feature.questionsession.domain.QuestionAnswerInput
import app.masary.feature.questionsession.domain.QuestionSessionExpiredException
import app.masary.feature.questionsession.domain.QuestionSessionNotFoundException
import app.masary.feature.questionsession.domain.QuestionSessionPackage
import app.masary.feature.questionsession.domain.QuestionSessionRepository
import app.masary.feature.questionsession.domain.QuestionSessionResult
import app.masary.feature.questionsession.domain.QuestionSessionSourceUnavailableException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

sealed interface QuestionSessionUiState {
    data object Loading : QuestionSessionUiState

    data class Content(
        val data: QuestionSessionPackage,
        val viewingIndex: Int = data.progress.currentIndex
            .coerceAtMost((data.questions.size - 1).coerceAtLeast(0)),
        val remainingSeconds: Int? = null,
        val isRefreshing: Boolean = false,
        val isSaving: Boolean = false,
        val message: String? = null,
    ) : QuestionSessionUiState

    data class CompletedLocal(
        val data: QuestionSessionPackage,
        val message: String = "تم حفظ جميع الإجابات على الجهاز. ستُرسل للخادم عند توفر الاتصال.",
    ) : QuestionSessionUiState

    data class Result(
        val data: QuestionSessionResult,
    ) : QuestionSessionUiState

    data class Error(val message: String) : QuestionSessionUiState
    data class Expired(val message: String) : QuestionSessionUiState
    data class SourceUnavailable(val message: String) : QuestionSessionUiState
}

class QuestionSessionViewModel(
    private val sessionId: String,
    private val repository: QuestionSessionRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<QuestionSessionUiState>(QuestionSessionUiState.Loading)
    val state: StateFlow<QuestionSessionUiState> = _state.asStateFlow()
    private var loadJob: Job? = null
    private var saveJob: Job? = null
    private var syncJob: Job? = null
    private var timerJob: Job? = null
    private var timerSessionId: String? = null
    private var activeTimeJob: Job? = null
    private var activeTimeSessionId: String? = null
    private var activeSeconds: Int = 0
    private var activeLastInteractionElapsed: Long = 0L
    private var activeLastCountedElapsed: Long = 0L

    init {
        viewModelScope.launch {
            state.collect { uiState ->
                if (uiState is QuestionSessionUiState.Content) {
                    ensureTimer(uiState)
                    ensureActiveTime(uiState)
                }
            }
        }
        load()
    }

    fun retry() {
        if (loadJob?.isActive == true) return
        load()
    }

    fun markUserActivity() {
        val current = _state.value as? QuestionSessionUiState.Content ?: return
        if (!current.data.policy.activeTime.enabled) return
        val now = (System.nanoTime() / 1_000_000L)
        accumulateActiveTime(current, now)
        activeLastInteractionElapsed = now
        activeLastCountedElapsed = now
    }

    fun previous() {
        markUserActivity()
        val current = _state.value as? QuestionSessionUiState.Content ?: return
        if (!current.data.policy.allowBack || current.isSaving) return
        if (current.viewingIndex <= 0) return
        _state.value = current.copy(
            viewingIndex = current.viewingIndex - 1,
            message = null,
        )
    }

    fun next() {
        markUserActivity()
        val current = _state.value as? QuestionSessionUiState.Content ?: return
        if (current.isSaving) return
        val lastIndex = current.data.questions.lastIndex
        val maxBrowsable = minOf(current.data.progress.currentIndex, lastIndex)
        if (current.viewingIndex >= maxBrowsable) return
        _state.value = current.copy(
            viewingIndex = current.viewingIndex + 1,
            message = null,
        )
    }

    fun finish() {
        if (saveJob?.isActive == true) return
        markUserActivity()
        val current = _state.value as? QuestionSessionUiState.Content ?: return
        if (current.data.progress.currentIndex < current.data.questions.size) return

        _state.value = current.copy(isSaving = true, message = null)
        saveJob = viewModelScope.launch {
            flushActiveTime(current)
            repository.markCompletedLocal(
                sessionId = current.data.session.id,
                allowIncomplete = false,
            ).onSuccess {
                    val refreshed = repository.loadSnapshot(current.data.session.id)
                        ?: current.data.copy(
                            session = current.data.session.copy(status = "completed_local"),
                        )
                    _state.value = QuestionSessionUiState.CompletedLocal(refreshed)
                    triggerPendingAnswerSync(refreshed.session.id)
                }
                .onFailure { error ->
                    _state.value = current.copy(
                        isSaving = false,
                        message = error.message ?: "تعذر إنهاء الاختبار الآن.",
                    )
                }
        }
    }

    fun submit(answer: QuestionAnswerInput) {
        if (saveJob?.isActive == true) return
        markUserActivity()
        val current = (_state.value as? QuestionSessionUiState.Content) ?: return
        val index = current.viewingIndex.coerceIn(0, current.data.questions.lastIndex)
        val question = current.data.questions.getOrNull(index) ?: return
        val frontier = current.data.progress.currentIndex.coerceIn(0, current.data.questions.size)
        val isRevision = index < frontier
        val nextFrontier = if (isRevision) {
            frontier
        } else {
            (index + 1).coerceAtMost(current.data.questions.size)
        }
        val lastNewAnswer = !isRevision && nextFrontier >= current.data.questions.size
        val autoComplete = lastNewAnswer && !current.data.policy.allowBack

        _state.value = current.copy(isSaving = true, message = null)
        saveJob = viewModelScope.launch {
            repository.saveLocalAnswer(
                sessionId = current.data.session.id,
                questionId = question.id,
                answer = answer,
                nextQuestionIndex = nextFrontier,
                completed = autoComplete,
            ).onSuccess {
                val refreshed = repository.loadSnapshot(current.data.session.id)
                    ?: current.data.copy(
                        progress = current.data.progress.copy(currentIndex = nextFrontier),
                    )
                if (autoComplete) {
                    flushActiveTime(current)
                    _state.value = QuestionSessionUiState.CompletedLocal(refreshed)
                    triggerPendingAnswerSync(refreshed.session.id)
                    return@onSuccess
                }

                val lastIndex = refreshed.questions.lastIndex
                val nextViewing = when {
                    lastIndex < 0 -> 0
                    isRevision -> (index + 1).coerceAtMost(
                        minOf(refreshed.progress.currentIndex, lastIndex),
                    )
                    refreshed.progress.currentIndex >= refreshed.questions.size -> lastIndex
                    else -> refreshed.progress.currentIndex.coerceIn(0, lastIndex)
                }
                _state.value = QuestionSessionUiState.Content(
                    data = refreshed,
                    viewingIndex = nextViewing,
                )
                triggerPendingAnswerSync()
            }.onFailure { error ->
                _state.value = current.copy(
                    isSaving = false,
                    message = error.message ?: "تعذر حفظ الإجابة. حاول مرة أخرى.",
                )
            }
        }
    }

    private fun ensureActiveTime(content: QuestionSessionUiState.Content) {
        val policy = content.data.policy.activeTime
        if (!policy.enabled) return
        if (activeTimeJob?.isActive == true &&
            activeTimeSessionId == content.data.session.id
        ) {
            return
        }

        activeTimeJob?.cancel()
        activeTimeSessionId = content.data.session.id
        activeTimeJob = viewModelScope.launch {
            activeSeconds = repository.loadActiveSeconds(content.data.session.id) ?: 0
            val now = (System.nanoTime() / 1_000_000L)
            activeLastInteractionElapsed = now
            activeLastCountedElapsed = now
            val intervalMillis = policy.pingInterval.coerceIn(5, 60) * 1_000L

            while (true) {
                delay(intervalMillis)
                val current = _state.value as? QuestionSessionUiState.Content ?: return@launch
                if (current.data.session.id != content.data.session.id) return@launch
                flushActiveTime(current)
            }
        }
    }

    private fun accumulateActiveTime(
        content: QuestionSessionUiState.Content,
        nowElapsed: Long,
    ) {
        if (!content.data.policy.activeTime.enabled) return
        if (activeLastCountedElapsed <= 0L) {
            activeLastCountedElapsed = nowElapsed
            activeLastInteractionElapsed = nowElapsed
            return
        }

        val deltaMillis = (nowElapsed - activeLastCountedElapsed).coerceAtLeast(0L)
        val idleMillis = content.data.policy.activeTime.idleSeconds
            .coerceIn(5, 900) * 1_000L
        val sinceInteraction = (nowElapsed - activeLastInteractionElapsed).coerceAtLeast(0L)
        if (deltaMillis > 0L && sinceInteraction <= idleMillis) {
            activeSeconds = (activeSeconds + (deltaMillis / 1_000L).toInt())
                .coerceIn(0, 86_400)
        }
        activeLastCountedElapsed = nowElapsed
    }

    private suspend fun flushActiveTime(content: QuestionSessionUiState.Content) {
        if (!content.data.policy.activeTime.enabled) return
        val now = (System.nanoTime() / 1_000_000L)
        accumulateActiveTime(content, now)
        repository.saveActiveSeconds(
            sessionId = content.data.session.id,
            seconds = activeSeconds,
        )
    }

    private fun ensureTimer(content: QuestionSessionUiState.Content) {
        val seconds = content.data.policy.timerSeconds
        val startedAt = content.data.session.startedAtEpochSeconds
        if (seconds <= 0 || startedAt <= 0L) return
        if (timerJob?.isActive == true && timerSessionId == content.data.session.id) return

        timerSessionId = content.data.session.id
        val deadlineEpochSeconds = startedAt + seconds
        timerJob = viewModelScope.launch {
            while (true) {
                val current = _state.value as? QuestionSessionUiState.Content ?: return@launch
                if (current.data.session.id != content.data.session.id) return@launch

                val nowEpochSeconds = System.currentTimeMillis() / 1000L
                val remaining = (deadlineEpochSeconds - nowEpochSeconds)
                    .coerceAtLeast(0L)
                    .coerceAtMost(Int.MAX_VALUE.toLong())
                    .toInt()
                if (current.remainingSeconds != remaining) {
                    _state.value = current.copy(remainingSeconds = remaining)
                }
                if (remaining <= 0) {
                    handleTimerExpired(content.data.session.id)
                    return@launch
                }
                delay(1_000L)
            }
        }
    }

    private suspend fun handleTimerExpired(expiredSessionId: String) {
        val current = _state.value as? QuestionSessionUiState.Content ?: return
        if (current.data.session.id != expiredSessionId) return

        saveJob?.takeIf { it.isActive }?.join()
        val latest = _state.value as? QuestionSessionUiState.Content ?: return
        flushActiveTime(latest)
        repository.markCompletedLocal(
            sessionId = expiredSessionId,
            allowIncomplete = true,
        ).onSuccess {
            val refreshed = repository.loadSnapshot(expiredSessionId)
                ?: latest.data.copy(
                    session = latest.data.session.copy(status = "completed_local"),
                )
            _state.value = QuestionSessionUiState.CompletedLocal(
                data = refreshed,
                message = "انتهى وقت الاختبار. تم حفظ الإجابات الموجودة وسيؤكد الخادم النتيجة.",
            )
            syncJob?.takeIf { it.isActive }?.join()
            triggerPendingAnswerSync(expiredSessionId)
        }.onFailure { error ->
            _state.value = latest.copy(
                isSaving = false,
                message = error.message ?: "انتهى الوقت وتعذر تثبيت الإنهاء محليًا.",
            )
        }
    }

    private fun triggerPendingAnswerSync(finishSessionId: String? = null) {
        if (syncJob?.isActive == true) return
        syncJob = viewModelScope.launch {
            val summary = repository.syncPendingAnswers().getOrNull() ?: return@launch
            if (finishSessionId == null || summary.retryScheduled > 0) {
                return@launch
            }
            repository.finishSession(finishSessionId)
                .onSuccess { result ->
                    _state.value = QuestionSessionUiState.Result(result)
                }
                .onFailure { error ->
                    val local = _state.value as? QuestionSessionUiState.CompletedLocal
                    if (local != null) {
                        _state.value = local.copy(
                            message = error.message
                                ?: "الإجابات محفوظة، لكن النتيجة لم تُؤكد من الخادم بعد.",
                        )
                    }
                }
        }
    }

    private fun load() {
        loadJob = viewModelScope.launch {
            repository.loadResult(sessionId)?.let { cachedResult ->
                _state.value = QuestionSessionUiState.Result(cachedResult)
                return@launch
            }

            val snapshot = repository.loadSnapshot(sessionId)
            if (snapshot != null) {
                val locallyCompleted = snapshot.session.status == "completed_local"
                _state.value = when {
                    locallyCompleted -> QuestionSessionUiState.CompletedLocal(snapshot)
                    snapshot.progress.currentIndex >= snapshot.questions.size &&
                        snapshot.policy.allowBack -> QuestionSessionUiState.Content(
                        data = snapshot,
                        viewingIndex = snapshot.questions.lastIndex.coerceAtLeast(0),
                        isRefreshing = true,
                    )
                    snapshot.progress.currentIndex >= snapshot.questions.size ->
                        QuestionSessionUiState.CompletedLocal(snapshot)
                    else -> QuestionSessionUiState.Content(snapshot, isRefreshing = true)
                }
            } else {
                _state.value = QuestionSessionUiState.Loading
            }

            val syncSummary = repository.syncPendingAnswers().getOrNull()
            if (snapshot != null &&
                snapshot.session.status == "completed_local" &&
                syncSummary != null &&
                syncSummary.retryScheduled == 0
            ) {
                val finish = repository.finishSession(snapshot.session.id)
                if (finish.isSuccess) {
                    _state.value = QuestionSessionUiState.Result(finish.getOrThrow())
                    return@launch
                }
            }

            repository.loadPackage(sessionId)
                .onSuccess { data ->
                    if (_state.value is QuestionSessionUiState.Result) {
                        return@onSuccess
                    }
                    val locallyCompleted = data.session.status == "completed_local"
                    when {
                        locallyCompleted -> {
                            _state.value = QuestionSessionUiState.CompletedLocal(data)
                            triggerPendingAnswerSync(data.session.id)
                        }
                        data.progress.currentIndex >= data.questions.size &&
                            data.policy.allowBack -> {
                            _state.value = QuestionSessionUiState.Content(
                                data = data,
                                viewingIndex = data.questions.lastIndex.coerceAtLeast(0),
                            )
                        }
                        data.progress.currentIndex >= data.questions.size -> {
                            _state.value = QuestionSessionUiState.CompletedLocal(data)
                            triggerPendingAnswerSync(data.session.id)
                        }
                        else -> {
                            _state.value = QuestionSessionUiState.Content(data)
                        }
                    }
                }
                .onFailure { error ->
                    if (_state.value is QuestionSessionUiState.Result) {
                        return@onFailure
                    }
                    if (snapshot != null) {
                        _state.value = when {
                            snapshot.session.status == "completed_local" -> {
                                QuestionSessionUiState.CompletedLocal(
                                    snapshot,
                                    message = "الجلسة محفوظة محليًا، وتعذر تأكيد النتيجة من الخادم الآن.",
                                )
                            }
                            snapshot.progress.currentIndex >= snapshot.questions.size &&
                                snapshot.policy.allowBack -> {
                                QuestionSessionUiState.Content(
                                    data = snapshot,
                                    viewingIndex = snapshot.questions.lastIndex.coerceAtLeast(0),
                                    isRefreshing = false,
                                    message = error.message ?: "يمكن مراجعة إجاباتك، وتعذر تحديث الجلسة الآن.",
                                )
                            }
                            snapshot.progress.currentIndex >= snapshot.questions.size -> {
                                QuestionSessionUiState.CompletedLocal(
                                    snapshot,
                                    message = "الجلسة محفوظة محليًا، وتعذر تأكيد النتيجة من الخادم الآن.",
                                )
                            }
                            else -> {
                                QuestionSessionUiState.Content(
                                    data = snapshot,
                                    isRefreshing = false,
                                    message = error.message ?: "تعذر تحديث الجلسة الآن.",
                                )
                            }
                        }
                    } else {
                        _state.value = when (error) {
                            is QuestionSessionExpiredException -> QuestionSessionUiState.Expired(
                                error.message ?: "انتهت صلاحية الجلسة.",
                            )
                            is QuestionSessionNotFoundException -> QuestionSessionUiState.Error(
                                error.message ?: "جلسة النشاط غير متاحة.",
                            )
                            is QuestionSessionSourceUnavailableException ->
                                QuestionSessionUiState.SourceUnavailable(
                                    error.message ?: "مصدر الأسئلة غير متاح.",
                                )
                            else -> QuestionSessionUiState.Error(
                                error.message ?: "تعذر تحميل جلسة الأسئلة.",
                            )
                        }
                    }
                }
        }
    }
}

class QuestionSessionViewModelFactory(
    private val sessionId: String,
    private val repository: QuestionSessionRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(QuestionSessionViewModel::class.java))
        return QuestionSessionViewModel(sessionId, repository) as T
    }
}
