package app.masary.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DataStoreOnboardingStore(
    private val dataStore: DataStore<Preferences>,
) : OnboardingStore {
    override val isCompleted: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[ONBOARDING_COMPLETED] ?: false
    }

    override suspend fun markCompleted() {
        dataStore.edit { it[ONBOARDING_COMPLETED] = true }
    }

    override suspend fun reset() {
        dataStore.edit { it.remove(ONBOARDING_COMPLETED) }
    }

    private companion object {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    }
}
