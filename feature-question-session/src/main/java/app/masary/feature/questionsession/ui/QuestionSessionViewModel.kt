package app.masary.feature.questionsession.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.masary.feature.questionsession.domain.QuestionAnswerInput
import app.masary.feature.questionsession.domain.QuestionSessionExpiredException
import app.masary.feature.questionsession.domain.QuestionSessionNotFoundException
import app.masary.feature.questionsession.domain.QuestionSessionPackage
import app.masary.feature.questionsession.domain.QuestionSessionRepository
import app.masary.feature.questionsession.domain.QuestionSessionSourceUnavailableException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface QuestionSessionUiState {
    data object Loading : QuestionSessionUiState

    data class Content(
        val data: QuestionSessionPackage,
        val isRefreshing: Boolean = false,
        val isSaving: Boolean = false,
        val message: String? = null,
    ) : QuestionSessionUiState

    data class CompletedLocal(
        val data: QuestionSessionPackage,
        val message: String = "تم حفظ جميع الإجابات على الجهاز. ستُرسل للخادم عند توفر الاتصال.",
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

    init {
        load()
    }

    fun retry() {
        if (loadJob?.isActive == true) return
        load()
    }

    fun submit(answer: QuestionAnswerInput) {
        if (saveJob?.isActive == true) return
        val current = (_state.value as? QuestionSessionUiState.Content) ?: return
        val index = current.data.progress.currentIndex
        val question = current.data.questions.getOrNull(index) ?: return
        val nextIndex = (index + 1).coerceAtMost(current.data.questions.size)
        val completed = nextIndex >= current.data.questions.size

        _state.value = current.copy(isSaving = true, message = null)
        saveJob = viewModelScope.launch {
            repository.saveLocalAnswer(
                sessionId = current.data.session.id,
                questionId = question.id,
                answer = answer,
                nextQuestionIndex = nextIndex,
                completed = completed,
            ).onSuccess {
                val refreshed = repository.loadSnapshot(current.data.session.id)
                    ?: current.data.copy(
                        progress = current.data.progress.copy(currentIndex = nextIndex),
                    )
                _state.value = if (completed || refreshed.progress.currentIndex >= refreshed.questions.size) {
                    QuestionSessionUiState.CompletedLocal(refreshed)
                } else {
                    QuestionSessionUiState.Content(refreshed)
                }
            }.onFailure { error ->
                _state.value = current.copy(
                    isSaving = false,
                    message = error.message ?: "تعذر حفظ الإجابة. حاول مرة أخرى.",
                )
            }
        }
    }

    private fun load() {
        loadJob = viewModelScope.launch {
            val snapshot = repository.loadSnapshot(sessionId)
            if (snapshot != null) {
                _state.value = if (snapshot.progress.currentIndex >= snapshot.questions.size) {
                    QuestionSessionUiState.CompletedLocal(snapshot)
                } else {
                    QuestionSessionUiState.Content(snapshot, isRefreshing = true)
                }
            } else {
                _state.value = QuestionSessionUiState.Loading
            }

            repository.loadPackage(sessionId)
                .onSuccess { data ->
                    _state.value = if (data.progress.currentIndex >= data.questions.size) {
                        QuestionSessionUiState.CompletedLocal(data)
                    } else {
                        QuestionSessionUiState.Content(data)
                    }
                }
                .onFailure { error ->
                    if (snapshot != null) {
                        _state.value = if (snapshot.progress.currentIndex >= snapshot.questions.size) {
                            QuestionSessionUiState.CompletedLocal(
                                snapshot,
                                message = "الجلسة محفوظة محليًا، وتعذر تحديثها من الخادم الآن.",
                            )
                        } else {
                            QuestionSessionUiState.Content(
                                data = snapshot,
                                isRefreshing = false,
                                message = error.message ?: "تعذر تحديث الجلسة الآن.",
                            )
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
