package app.masary.feature.subject.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.masary.feature.subject.domain.StudentSubjectPage
import app.masary.feature.subject.domain.SubjectSnapshotMetadata
import app.masary.feature.subject.domain.SubjectSnapshotStore
import com.google.gson.Gson
import kotlinx.coroutines.flow.first

class DataStoreSubjectSnapshotStore(
    private val dataStore: DataStore<Preferences>,
    private val gson: Gson = Gson(),
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
    private val maxAgeMillis: Long = DEFAULT_MAX_AGE_MILLIS,
) : SubjectSnapshotStore {
    override suspend fun read(studentId: String, subjectVersionId: Int): StudentSubjectPage? {
        if (studentId.isBlank() || subjectVersionId <= 0) return null
        val values = dataStore.data.first()
        if (values[OWNER] != studentId || values[SCHEMA_VERSION] != CURRENT_SCHEMA_VERSION) {
            return evictAll()
        }
        val savedAt = values[savedAtKey(subjectVersionId)] ?: return null
        val age = nowEpochMillis() - savedAt
        if (age !in 0..maxAgeMillis) {
            evictSubject(subjectVersionId)
            return null
        }
        val json = values[payloadKey(subjectVersionId)] ?: return null
        val decoded = runCatching { gson.fromJson(json, StudentSubjectPage::class.java) }.getOrNull()
        if (
            decoded == null ||
            decoded.studentId != studentId ||
            decoded.subjectVersionId != subjectVersionId ||
            decoded.version.isBlank()
        ) {
            evictSubject(subjectVersionId)
            return null
        }
        return decoded.copy(snapshot = SubjectSnapshotMetadata(savedAt))
    }

    override suspend fun write(studentId: String, subjectVersionId: Int, data: StudentSubjectPage) {
        require(studentId.isNotBlank()) { "Subject snapshot owner is required" }
        require(subjectVersionId > 0) { "Subject snapshot id must be positive" }
        require(data.studentId == studentId) { "Subject snapshot owner mismatch" }
        require(data.subjectVersionId == subjectVersionId) { "Subject snapshot id mismatch" }
        require(data.version.isNotBlank()) { "Subject snapshot version is required" }

        val currentOwner = dataStore.data.first()[OWNER]
        if (currentOwner != null && currentOwner != studentId) clear()
        dataStore.edit { preferences ->
            preferences[OWNER] = studentId
            preferences[SCHEMA_VERSION] = CURRENT_SCHEMA_VERSION
            preferences[payloadKey(subjectVersionId)] = gson.toJson(data.copy(snapshot = null))
            preferences[savedAtKey(subjectVersionId)] = nowEpochMillis()
        }
    }

    override suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    private suspend fun evictAll(): StudentSubjectPage? {
        clear()
        return null
    }

    private suspend fun evictSubject(subjectVersionId: Int) {
        dataStore.edit { preferences ->
            preferences.remove(payloadKey(subjectVersionId))
            preferences.remove(savedAtKey(subjectVersionId))
        }
    }

    private fun payloadKey(subjectVersionId: Int) =
        stringPreferencesKey("subject_page_payload_$subjectVersionId")

    private fun savedAtKey(subjectVersionId: Int) =
        longPreferencesKey("subject_page_saved_at_$subjectVersionId")

    private companion object {
        val OWNER = stringPreferencesKey("subject_page_owner")
        val SCHEMA_VERSION = intPreferencesKey("subject_page_schema_version")
        const val CURRENT_SCHEMA_VERSION = 2
        const val DEFAULT_MAX_AGE_MILLIS = 24L * 60L * 60L * 1_000L
    }
}
