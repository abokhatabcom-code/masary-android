package app.masary.feature.subjects.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.masary.feature.subjects.domain.StudentSubjectsData
import app.masary.feature.subjects.domain.SubjectsSnapshotMetadata
import app.masary.feature.subjects.domain.SubjectsSnapshotStore
import com.google.gson.Gson
import kotlinx.coroutines.flow.first

class DataStoreSubjectsSnapshotStore(
    private val dataStore: DataStore<Preferences>,
    private val gson: Gson = Gson(),
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) : SubjectsSnapshotStore {
    override suspend fun read(studentId: String): StudentSubjectsData? {
        val values = dataStore.data.first()
        if (values[OWNER] != studentId) return evictInvalidSnapshot()

        val savedAt = values[SAVED_AT] ?: return evictInvalidSnapshot()
        if (values[SCHEMA_VERSION] != CURRENT_SCHEMA_VERSION) {
            return evictInvalidSnapshot()
        }

        val json = values[PAYLOAD] ?: return evictInvalidSnapshot()
        val decoded = runCatching { gson.fromJson(json, StudentSubjectsData::class.java) }.getOrNull()
            ?: return evictInvalidSnapshot()
        if (decoded.studentId != studentId || decoded.version.isBlank()) return evictInvalidSnapshot()

        return decoded.copy(snapshot = SubjectsSnapshotMetadata(savedAt))
    }

    override suspend fun write(studentId: String, data: StudentSubjectsData) {
        require(data.studentId == studentId) { "Subjects snapshot owner mismatch" }
        require(data.version.isNotBlank()) { "Subjects snapshot version is required" }
        dataStore.edit {
            it[OWNER] = studentId
            it[PAYLOAD] = gson.toJson(data.copy(snapshot = null))
            it[SAVED_AT] = nowEpochMillis()
            it[SCHEMA_VERSION] = CURRENT_SCHEMA_VERSION
        }
    }

    override suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    private suspend fun evictInvalidSnapshot(): StudentSubjectsData? {
        clear()
        return null
    }

    private companion object {
        val OWNER = stringPreferencesKey("subjects_snapshot_owner")
        val PAYLOAD = stringPreferencesKey("subjects_snapshot_payload")
        val SAVED_AT = longPreferencesKey("subjects_snapshot_saved_at")
        val SCHEMA_VERSION = intPreferencesKey("subjects_snapshot_schema_version")
        const val CURRENT_SCHEMA_VERSION = 1
    }
}
