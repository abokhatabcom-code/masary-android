package app.masary.feature.home.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.masary.feature.home.domain.HomeRepository
import app.masary.feature.home.domain.HomeSessionExpiredException
import app.masary.feature.home.domain.StudentHomeData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

class StudentHomeViewModel(
    private val repository: HomeRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state: StateFlow<HomeUiState> = _state.asStateFlow()
    private var loadJob: Job? = null

    init {
        loadHome()
    }

    fun refresh() {
        val current = _state.value
        if (loadJob?.isActive == true) return
        loadHome(isRefresh = true)
    }

    private fun loadHome(isRefresh: Boolean = false) {
        val previous = currentData()
        _state.value = when {
            isRefresh && previous != null -> HomeUiState.Content(previous, isRefreshing = true)
            previous != null -> HomeUiState.Content(previous)
            else -> HomeUiState.Loading
        }

        loadJob = viewModelScope.launch {
            val snapshot = if (previous == null) repository.loadSnapshot() else null
            if (snapshot != null) {
                _state.value = HomeUiState.Content(snapshot, isRefreshing = true)
            }
            repository.loadHome()
                .onSuccess { data ->
                    _state.value = HomeUiState.Content(data)
                }
                .onFailure { error ->
                    _state.value = if (error is HomeSessionExpiredException) {
                        HomeUiState.SessionExpired
                    } else {
                        HomeUiState.Error(
                            message = error.message ?: "تعذر تحميل الصفحة الرئيسية.",
                            previousData = previous ?: snapshot,
                        )
                    }
                }
        }
    }

    private fun currentData(): StudentHomeData? = when (val current = _state.value) {
        is HomeUiState.Content -> current.data
        is HomeUiState.Error -> current.previousData
        HomeUiState.Loading,
        HomeUiState.SessionExpired,
        -> null
    }
}

class StudentHomeViewModelFactory(
    private val repository: HomeRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(StudentHomeViewModel::class.java))
        return StudentHomeViewModel(repository) as T
    }
}
