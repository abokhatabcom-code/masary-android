package app.masary.core.datastore

import kotlinx.coroutines.flow.Flow

/** Local contract kept separate from authentication so onboarding can be shown again explicitly. */
interface OnboardingStore {
    val isCompleted: Flow<Boolean>
    suspend fun markCompleted()
    suspend fun reset()
}
