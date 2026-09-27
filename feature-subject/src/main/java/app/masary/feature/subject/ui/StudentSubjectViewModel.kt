package app.masary.feature.subject.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.masary.feature.subject.domain.StudentSubjectPage
import app.masary.feature.subject.domain.SubjectPageNotFoundException
import app.masary.feature.subject.domain.SubjectPageSessionExpiredException
import app.masary.feature.subject.domain.SubjectRepository
import app.masary.core.models.student.StudentLiveState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SubjectUiState {
    data object Loading : SubjectUiState
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
) : ViewModel() {
    private val _state = MutableStateFlow<SubjectUiState>(SubjectUiState.Loading)
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
                        _state.value = current.copy(data = current.data.applyLiveState(liveState))
                    }
                    is SubjectUiState.Error -> {
                        current.previousData?.let { previous ->
                            _state.value = current.copy(previousData = previous.applyLiveState(liveState))
                        }
                    }
                    SubjectUiState.Loading,
                    SubjectUiState.SessionExpired,
                    is SubjectUiState.NotFound,
                    -> Unit
                }
            }
        }
    }

    fun refresh() {
        if (subjectVersionId <= 0 || loadJob?.isActive == true) return
        loadSubject(isRefresh = true)
    }

    private fun loadSubject(isRefresh: Boolean = false) {
        val previous = currentData()
        _state.value = when {
            isRefresh && previous != null -> SubjectUiState.Content(previous, isRefreshing = true)
            previous != null -> SubjectUiState.Content(previous)
            else -> SubjectUiState.Loading
        }
        loadJob = viewModelScope.launch {
            val snapshot = if (previous == null) repository.loadSnapshot(subjectVersionId) else null
            if (snapshot != null) {
                _state.value = SubjectUiState.Content(snapshot, isRefreshing = true)
            }
            repository.loadSubject(subjectVersionId)
                .onSuccess { data -> _state.value = SubjectUiState.Content(data) }
                .onFailure { error ->
                    _state.value = when (error) {
                        is SubjectPageSessionExpiredException -> SubjectUiState.SessionExpired
                        is SubjectPageNotFoundException -> SubjectUiState.NotFound(error.message ?: "المادة غير متاحة.")
                        else -> {
                            val stale = previous ?: snapshot
                            if (stale == null) {
                                SubjectUiState.Error(error.message ?: "تعذر تحميل المادة.")
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

    private fun currentData(): StudentSubjectPage? = when (val current = _state.value) {
        is SubjectUiState.Content -> current.data
        is SubjectUiState.Error -> current.previousData
        SubjectUiState.Loading,
        SubjectUiState.SessionExpired,
        is SubjectUiState.NotFound,
        -> null
    }
}

class StudentSubjectViewModelFactory(
    private val subjectVersionId: Int,
    private val repository: SubjectRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(StudentSubjectViewModel::class.java))
        return StudentSubjectViewModel(subjectVersionId, repository) as T
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
