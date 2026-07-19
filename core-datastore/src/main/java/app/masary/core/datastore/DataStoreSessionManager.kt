package app.masary.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DataStoreSessionManager(private val dataStore: DataStore<Preferences>) : SessionManager {
    override val session: Flow<StudentSession?> = dataStore.data.map { values ->
        val id = values[STUDENT_ID] ?: return@map null
        val name = values[STUDENT_NAME] ?: return@map null
        val token = values[ACCESS_TOKEN] ?: return@map null
        StudentSession(id, name, token)
    }

    override suspend fun save(session: StudentSession) {
        dataStore.edit { values ->
            values[STUDENT_ID] = session.studentId
            values[STUDENT_NAME] = session.studentName
            values[ACCESS_TOKEN] = session.accessToken
        }
    }

    override suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    private companion object {
        val STUDENT_ID = stringPreferencesKey("student_id")
        val STUDENT_NAME = stringPreferencesKey("student_name")
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
    }
}
