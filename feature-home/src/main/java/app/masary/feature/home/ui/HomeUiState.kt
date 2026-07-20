package app.masary.feature.home.ui

import app.masary.feature.home.domain.StudentHomeData

sealed interface HomeUiState {
    data object Loading : HomeUiState

    data class Content(
        val data: StudentHomeData,
        val isRefreshing: Boolean = false,
        val refreshMessage: String? = null,
    ) : HomeUiState

    data class Error(
        val message: String,
        val previousData: StudentHomeData? = null,
    ) : HomeUiState

    data object SessionExpired : HomeUiState
}
