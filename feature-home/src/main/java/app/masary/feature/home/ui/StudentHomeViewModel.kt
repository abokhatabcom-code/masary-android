package app.masary.feature.home.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.masary.feature.home.domain.HomeRepository
import app.masary.feature.home.domain.HomeSessionExpiredException
import app.masary.feature.home.domain.StudentHomeData
import app.masary.core.models.student.StudentLiveState
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
        observeLiveState()
        loadHome()
    }

    private fun observeLiveState() {
        viewModelScope.launch {
            repository.observeLiveState().collect { liveState ->
                when (val current = _state.value) {
                    is HomeUiState.Content -> {
                        _state.value = current.copy(data = current.data.applyLiveState(liveState))
                    }
                    is HomeUiState.Error -> {
                        current.previousData?.let { previous ->
                            _state.value = current.copy(previousData = previous.applyLiveState(liveState))
                        }
                    }
                    HomeUiState.Loading,
                    HomeUiState.SessionExpired,
                    -> Unit
                }
            }
        }
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


private fun StudentHomeData.applyLiveState(liveState: StudentLiveState): StudentHomeData {
    val profile = liveState.profile
    val updatedSummary = profile?.let {
        summary.copy(
            globalXp = it.globalXp ?: summary.globalXp,
            gems = it.gems ?: summary.gems,
            level = it.level ?: summary.level,
            levelPercent = it.levelProgressPercent ?: summary.levelPercent,
            levelNextXp = it.levelNextXp ?: summary.levelNextXp,
        )
    } ?: summary
    val updatedToday = profile?.let {
        today.copy(
            xp = it.todayXp ?: today.xp,
            seconds = it.todaySeconds ?: today.seconds,
            minutes = it.todayMinutes ?: today.minutes,
            attempts = it.todayAttempts ?: today.attempts,
        )
    } ?: today
    val updatedStreak = profile?.let {
        streak.copy(currentDays = it.streakCurrentDays ?: streak.currentDays)
    } ?: streak
    val updatedNotifications = profile?.let {
        notifications.copy(unreadCount = it.unreadNotifications ?: notifications.unreadCount)
    } ?: notifications
    val updatedGuide = profile?.let {
        val completedSteps = it.smartGuideCompletedSteps ?: smartGuide.completedSteps
        val totalSteps = it.smartGuideTotalSteps ?: smartGuide.totalSteps
        smartGuide.copy(
            completedSteps = completedSteps,
            totalSteps = totalSteps,
            completionPercent = it.smartGuideCompletionPercent ?: smartGuide.completionPercent,
            isComplete = totalSteps > 0 && completedSteps >= totalSteps,
        )
    } ?: smartGuide
    val updatedSubjects = subjects.map { subject ->
        liveState.subjects[subject.subjectVersionId]?.let { local ->
            subject.copy(
                hearts = local.hearts ?: subject.hearts,
                progressPercent = local.levelProgressPercent ?: subject.progressPercent,
                points = local.points ?: subject.points,
                level = local.level ?: subject.level,
            )
        } ?: subject
    }
    return copy(
        summary = updatedSummary,
        today = updatedToday,
        streak = updatedStreak,
        notifications = updatedNotifications,
        smartGuide = updatedGuide,
        indicators = indicators.copy(
            totalXp = updatedSummary.globalXp,
            gems = updatedSummary.gems,
            streakDays = updatedStreak.currentDays,
        ),
        subjects = updatedSubjects,
    )
}
