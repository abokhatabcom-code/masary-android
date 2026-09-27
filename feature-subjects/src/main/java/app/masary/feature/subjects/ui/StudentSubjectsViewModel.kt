package app.masary.feature.subjects.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.masary.feature.subjects.domain.StudentSubjectsData
import app.masary.core.models.student.StudentLiveState
import app.masary.feature.subjects.domain.SubjectsRepository
import app.masary.feature.subjects.domain.SubjectsSessionExpiredException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SubjectsUiState {
    data object Loading : SubjectsUiState
    data object SessionExpired : SubjectsUiState
    data class Content(
        val data: StudentSubjectsData,
        val isRefreshing: Boolean = false,
        val refreshMessage: String? = null,
    ) : SubjectsUiState
    data class Error(
        val message: String,
        val previousData: StudentSubjectsData? = null,
    ) : SubjectsUiState
}

class StudentSubjectsViewModel(
    private val repository: SubjectsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<SubjectsUiState>(SubjectsUiState.Loading)
    val state: StateFlow<SubjectsUiState> = _state.asStateFlow()
    private var loadJob: Job? = null

    init {
        observeLiveState()
        loadSubjects()
    }

    private fun observeLiveState() {
        viewModelScope.launch {
            repository.observeLiveState().collect { liveState ->
                when (val current = _state.value) {
                    is SubjectsUiState.Content -> {
                        _state.value = current.copy(data = current.data.applyLiveState(liveState))
                    }
                    is SubjectsUiState.Error -> {
                        current.previousData?.let { previous ->
                            _state.value = current.copy(previousData = previous.applyLiveState(liveState))
                        }
                    }
                    SubjectsUiState.Loading,
                    SubjectsUiState.SessionExpired,
                    -> Unit
                }
            }
        }
    }

    fun refresh() {
        if (loadJob?.isActive == true) return
        loadSubjects(isRefresh = true)
    }

    private fun loadSubjects(isRefresh: Boolean = false) {
        val previous = currentData()
        _state.value = when {
            isRefresh && previous != null -> SubjectsUiState.Content(previous, isRefreshing = true)
            previous != null -> SubjectsUiState.Content(previous)
            else -> SubjectsUiState.Loading
        }

        loadJob = viewModelScope.launch {
            val snapshot = if (previous == null) repository.loadSnapshot() else null
            if (snapshot != null) {
                _state.value = SubjectsUiState.Content(snapshot, isRefreshing = true)
            }
            repository.loadSubjects()
                .onSuccess { data -> _state.value = SubjectsUiState.Content(data) }
                .onFailure { error ->
                    _state.value = if (error is SubjectsSessionExpiredException) {
                        SubjectsUiState.SessionExpired
                    } else {
                        val stale = previous ?: snapshot
                        if (stale == null) {
                            SubjectsUiState.Error(error.message ?: "تعذر تحميل المواد.")
                        } else {
                            SubjectsUiState.Content(
                                data = stale,
                                refreshMessage = error.message ?: "تعذر تحديث المواد.",
                            )
                        }
                    }
                }
        }
    }

    private fun currentData(): StudentSubjectsData? = when (val current = _state.value) {
        is SubjectsUiState.Content -> current.data
        is SubjectsUiState.Error -> current.previousData
        SubjectsUiState.Loading,
        SubjectsUiState.SessionExpired,
        -> null
    }
}

class StudentSubjectsViewModelFactory(
    private val repository: SubjectsRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(StudentSubjectsViewModel::class.java))
        return StudentSubjectsViewModel(repository) as T
    }
}


private fun StudentSubjectsData.applyLiveState(liveState: StudentLiveState): StudentSubjectsData =
    copy(
        subjects = subjects.map { subject ->
            liveState.subjects[subject.subjectVersionId]?.let { local ->
                subject.copy(
                    hearts = local.hearts ?: subject.hearts,
                    points = local.points,
                    level = local.level,
                    progress = subject.progress.copy(
                        available = local.levelProgressPercent != null,
                        percent = local.levelProgressPercent,
                    ),
                )
            } ?: subject
        },
    )
