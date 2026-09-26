package app.masary.feature.trainingcenter.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.masary.feature.trainingcenter.domain.StudentTrainingCenter
import app.masary.feature.trainingcenter.domain.TrainingCenterNotFoundException
import app.masary.feature.trainingcenter.domain.TrainingCenterRepository
import app.masary.feature.trainingcenter.domain.TrainingCenterSessionExpiredException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface TrainingCenterUiState {
    data object Loading : TrainingCenterUiState
    data object SessionExpired : TrainingCenterUiState
    data class NotFound(val message: String) : TrainingCenterUiState
    data class Content(
        val data: StudentTrainingCenter,
        val isRefreshing: Boolean = false,
        val refreshMessage: String? = null,
    ) : TrainingCenterUiState
    data class Error(
        val message: String,
        val previousData: StudentTrainingCenter? = null,
    ) : TrainingCenterUiState
}

class StudentTrainingCenterViewModel(
    private val subjectVersionId: Int,
    private val repository: TrainingCenterRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<TrainingCenterUiState>(TrainingCenterUiState.Loading)
    val state: StateFlow<TrainingCenterUiState> = _state.asStateFlow()
    private var loadJob: Job? = null

    init {
        if (subjectVersionId <= 0) {
            _state.value = TrainingCenterUiState.NotFound("معرف المادة غير صالح.")
        } else {
            loadTrainingCenter()
        }
    }

    fun refresh() {
        if (subjectVersionId <= 0 || loadJob?.isActive == true) return
        loadTrainingCenter(isRefresh = true)
    }

    private fun loadTrainingCenter(isRefresh: Boolean = false) {
        val previous = currentData()
        _state.value = when {
            isRefresh && previous != null -> TrainingCenterUiState.Content(previous, isRefreshing = true)
            previous != null -> TrainingCenterUiState.Content(previous)
            else -> TrainingCenterUiState.Loading
        }
        loadJob = viewModelScope.launch {
            val snapshot = if (previous == null) repository.loadSnapshot(subjectVersionId) else null
            if (snapshot != null) {
                _state.value = TrainingCenterUiState.Content(snapshot, isRefreshing = true)
            }
            repository.loadTrainingCenter(subjectVersionId)
                .onSuccess { data -> _state.value = TrainingCenterUiState.Content(data) }
                .onFailure { error ->
                    _state.value = when (error) {
                        is TrainingCenterSessionExpiredException -> TrainingCenterUiState.SessionExpired
                        is TrainingCenterNotFoundException -> TrainingCenterUiState.NotFound(
                            error.message ?: "مركز التدريب غير متاح لهذه المادة.",
                        )
                        else -> {
                            val stale = previous ?: snapshot
                            if (stale == null) {
                                TrainingCenterUiState.Error(
                                    error.message ?: "تعذر تحميل مركز التدريب.",
                                )
                            } else {
                                TrainingCenterUiState.Content(
                                    data = stale,
                                    refreshMessage = error.message ?: "تعذر تحديث مركز التدريب.",
                                )
                            }
                        }
                    }
                }
        }
    }

    private fun currentData(): StudentTrainingCenter? = when (val current = _state.value) {
        is TrainingCenterUiState.Content -> current.data
        is TrainingCenterUiState.Error -> current.previousData
        TrainingCenterUiState.Loading,
        TrainingCenterUiState.SessionExpired,
        is TrainingCenterUiState.NotFound,
        -> null
    }
}

class StudentTrainingCenterViewModelFactory(
    private val subjectVersionId: Int,
    private val repository: TrainingCenterRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(StudentTrainingCenterViewModel::class.java))
        return StudentTrainingCenterViewModel(subjectVersionId, repository) as T
    }
}
