package app.masary.feature.subject.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.masary.core.models.student.StudentLiveState
import app.masary.feature.subject.domain.StudentSubjectPage
import app.masary.feature.subject.domain.SubjectPageNotFoundException
import app.masary.feature.subject.domain.SubjectPageSessionExpiredException
import app.masary.feature.subject.domain.SubjectRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SubjectUiState {
    data class Loading(
        val subjectVersionId: Int,
        val subjectName: String = "",
        val curriculumLabel: String = "",
    ) : SubjectUiState

    data object SessionExpired : SubjectUiState

    data class NotFound(val message: String) : SubjectUiState

    data class Content(
        val data: StudentSubjectPage,
        val isRefreshing: Boolean = false,
        val refreshMessage: String? = null,
    ) : SubjectUiState

    data class Error(
        val message: String,
        val previousData: StudentSubjectPage? = null,
    ) : SubjectUiState
}

class StudentSubjectViewModel(
    private val subjectVersionId: Int,
    private val repository: SubjectRepository,
    private val requestedSubjectName: String = "",
    private val requestedCurriculumLabel: String = "",
) : ViewModel() {
    private val loadingState = SubjectUiState.Loading(
        subjectVersionId = subjectVersionId,
        subjectName = requestedSubjectName,
        curriculumLabel = requestedCurriculumLabel,
    )
    private val _state = MutableStateFlow<SubjectUiState>(loadingState)
    val state: StateFlow<SubjectUiState> = _state.asStateFlow()
    private var loadJob: Job? = null

    init {
        if (subjectVersionId <= 0) {
            _state.value = SubjectUiState.NotFound("معرف المادة غير صالح.")
        } else {
            observeLiveState()
            loadSubject()
        }
    }

    private fun observeLiveState() {
        viewModelScope.launch {
            repository.observeLiveState().collect { liveState ->
                when (val current = _state.value) {
                    is SubjectUiState.Content -> {
                        if (current.data.subjectVersionId == subjectVersionId) {
                            _state.value = current.copy(
                                data = current.data.applyLiveState(liveState),
                            )
                        }
                    }

                    is SubjectUiState.Error -> {
                        current.previousData
                            ?.takeIf { it.subjectVersionId == subjectVersionId }
                            ?.let { previous ->
                                _state.value = current.copy(
                                    previousData = previous.applyLiveState(liveState),
                                )
                            }
                    }

                    is SubjectUiState.Loading,
                    SubjectUiState.SessionExpired,
                    is SubjectUiState.NotFound,
                    -> Unit
                }
            }
        }
    }

    fun refresh() {
        if (subjectVersionId <= 0 || loadJob?.isActive == true) return
        loadSubject(isUserRefresh = true)
    }

    private fun loadSubject(isUserRefresh: Boolean = false) {
        val previous = currentData()
        _state.value = when {
            previous != null && isUserRefresh -> SubjectUiState.Content(
                data = previous,
                isRefreshing = true,
            )
            previous != null -> SubjectUiState.Content(previous)
            else -> loadingState
        }

        loadJob = viewModelScope.launch {
            val snapshot = if (previous == null) {
                repository.loadSnapshot(subjectVersionId)
                    ?.takeIf(::belongsToRequestedSubject)
            } else {
                null
            }

            if (snapshot != null) {
                // Automatic refresh is intentionally silent: the correct cached page remains
                // visible and the network response updates it in-place when it arrives.
                _state.value = SubjectUiState.Content(
                    data = snapshot,
                    isRefreshing = isUserRefresh,
                )
            }

            repository.loadSubject(subjectVersionId)
                .onSuccess { data ->
                    if (belongsToRequestedSubject(data)) {
                        _state.value = SubjectUiState.Content(data)
                    } else {
                        keepCurrentOrReject(
                            previous = previous ?: snapshot,
                            message = "تم تجاهل بيانات لا تطابق المادة المطلوبة.",
                        )
                    }
                }
                .onFailure { error ->
                    _state.value = when (error) {
                        is SubjectPageSessionExpiredException -> SubjectUiState.SessionExpired
                        is SubjectPageNotFoundException -> SubjectUiState.NotFound(
                            error.message ?: "المادة غير متاحة.",
                        )
                        else -> {
                            val stale = (previous ?: snapshot)
                                ?.takeIf(::belongsToRequestedSubject)
                            if (stale == null) {
                                SubjectUiState.Error(
                                    error.message ?: "تعذر تحميل المادة.",
                                )
                            } else {
                                SubjectUiState.Content(
                                    data = stale,
                                    refreshMessage = error.message ?: "تعذر تحديث المادة.",
                                )
                            }
                        }
                    }
                }
        }
    }

    private fun keepCurrentOrReject(previous: StudentSubjectPage?, message: String) {
        val safe = previous?.takeIf(::belongsToRequestedSubject)
        _state.value = if (safe != null) {
            SubjectUiState.Content(data = safe, refreshMessage = message)
        } else {
            SubjectUiState.Error(message)
        }
    }

    private fun belongsToRequestedSubject(data: StudentSubjectPage): Boolean =
        data.subjectVersionId == subjectVersionId

    private fun currentData(): StudentSubjectPage? = when (val current = _state.value) {
        is SubjectUiState.Content -> current.data.takeIf(::belongsToRequestedSubject)
        is SubjectUiState.Error -> current.previousData?.takeIf(::belongsToRequestedSubject)
        is SubjectUiState.Loading,
        SubjectUiState.SessionExpired,
        is SubjectUiState.NotFound,
        -> null
    }
}

class StudentSubjectViewModelFactory(
    private val subjectVersionId: Int,
    private val repository: SubjectRepository,
    private val subjectName: String = "",
    private val curriculumLabel: String = "",
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(StudentSubjectViewModel::class.java))
        return StudentSubjectViewModel(
            subjectVersionId = subjectVersionId,
            repository = repository,
            requestedSubjectName = subjectName,
            requestedCurriculumLabel = curriculumLabel,
        ) as T
    }
}

private fun StudentSubjectPage.applyLiveState(liveState: StudentLiveState): StudentSubjectPage {
    val local = liveState.subjects[subjectVersionId] ?: return this
    return copy(
        points = local.points?.let { value ->
            points.copy(available = true, value = value)
        } ?: points,
        level = local.level?.let { value ->
            level.copy(available = true, value = value)
        } ?: level,
        progress = local.levelProgressPercent?.let { percent ->
            progress.copy(available = true, percent = percent)
        } ?: progress,
        hearts = hearts.copy(
            current = local.hearts?.coerceIn(0, hearts.maximum) ?: hearts.current,
        ),
    )
}
