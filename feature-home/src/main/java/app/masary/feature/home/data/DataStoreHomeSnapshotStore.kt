package app.masary.feature.home.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.masary.feature.home.domain.HomeSnapshotMetadata
import app.masary.feature.home.domain.HomeSnapshotStore
import app.masary.feature.home.domain.StudentHomeData
import com.google.gson.Gson
import kotlinx.coroutines.flow.first

class DataStoreHomeSnapshotStore(
    private val dataStore: DataStore<Preferences>,
    private val gson: Gson = Gson(),
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
    private val maxAgeMillis: Long = DEFAULT_MAX_AGE_MILLIS,
) : HomeSnapshotStore {
    override suspend fun read(studentId: String): StudentHomeData? {
        val values = dataStore.data.first()
        val owner = values[OWNER] ?: return null
        if (owner != studentId) return evictInvalidSnapshot()

        val savedAt = values[SAVED_AT] ?: return evictInvalidSnapshot()
        val age = nowEpochMillis() - savedAt
        if (values[SCHEMA_VERSION] != CURRENT_SCHEMA_VERSION || age !in 0..maxAgeMillis) {
            return evictInvalidSnapshot()
        }

        val json = values[PAYLOAD] ?: return evictInvalidSnapshot()
        val decoded = runCatching { gson.fromJson(json, StudentHomeData::class.java) }.getOrNull()
            ?: return evictInvalidSnapshot()
        if (decoded.student.id != studentId) return evictInvalidSnapshot()

        return decoded.copy(snapshot = HomeSnapshotMetadata(savedAt))
    }

    override suspend fun write(studentId: String, data: StudentHomeData) {
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

    private suspend fun evictInvalidSnapshot(): StudentHomeData? {
        clear()
        return null
    }

    private companion object {
        val OWNER = stringPreferencesKey("home_snapshot_owner")
        val PAYLOAD = stringPreferencesKey("home_snapshot_payload")
        val SAVED_AT = longPreferencesKey("home_snapshot_saved_at")
        val SCHEMA_VERSION = intPreferencesKey("home_snapshot_schema_version")
        const val CURRENT_SCHEMA_VERSION = 2
        const val DEFAULT_MAX_AGE_MILLIS = 24L * 60L * 60L * 1_000L
    }
}
