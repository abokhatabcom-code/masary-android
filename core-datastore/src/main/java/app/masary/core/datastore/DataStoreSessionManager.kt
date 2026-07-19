package app.masary.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.masary.core.models.auth.AuthenticatedStudent
import app.masary.core.models.auth.StudentSession
import app.masary.core.security.TokenStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DataStoreSessionManager(
    private val dataStore: DataStore<Preferences>,
    private val tokenStore: TokenStore,
) : SessionManager {
    override val session: Flow<StudentSession?> = dataStore.data.map { values ->
        val id = values[STUDENT_ID] ?: return@map null
        val username = values[STUDENT_USERNAME] ?: return@map null
        val displayName = values[STUDENT_DISPLAY_NAME] ?: return@map null
        StudentSession(id, username, displayName)
    }

    override suspend fun save(authenticatedStudent: AuthenticatedStudent) {
        tokenStore.save(authenticatedStudent.tokens)
        try {
            dataStore.edit { values ->
                values[STUDENT_ID] = authenticatedStudent.student.id
                values[STUDENT_USERNAME] = authenticatedStudent.student.username
                values[STUDENT_DISPLAY_NAME] = authenticatedStudent.student.displayName
            }
        } catch (error: Throwable) {
            tokenStore.clear()
            throw error
        }
    }

    override suspend fun clear() {
        val localResult = runCatching { dataStore.edit { it.clear() } }
        tokenStore.clear()
        localResult.getOrThrow()
    }

    private companion object {
        val STUDENT_ID = stringPreferencesKey("student_id")
        val STUDENT_USERNAME = stringPreferencesKey("student_username")
        val STUDENT_DISPLAY_NAME = stringPreferencesKey("student_display_name")
    }
}
