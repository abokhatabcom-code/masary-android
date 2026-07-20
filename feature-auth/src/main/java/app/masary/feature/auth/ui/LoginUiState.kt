package app.masary.feature.auth.ui

import app.masary.core.models.auth.StudentSession

sealed interface LoginUiState {
    data object Restoring : LoginUiState
    data object Idle : LoginUiState
    data object Loading : LoginUiState
    data class Success(val session: StudentSession) : LoginUiState
    data class Error(val message: String) : LoginUiState
}
