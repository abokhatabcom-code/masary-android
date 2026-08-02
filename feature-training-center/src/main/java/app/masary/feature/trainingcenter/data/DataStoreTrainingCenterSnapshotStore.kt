package app.masary.feature.trainingcenter.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.masary.feature.trainingcenter.domain.StudentTrainingCenter
import app.masary.feature.trainingcenter.domain.TrainingCenterSnapshotMetadata
import app.masary.feature.trainingcenter.domain.TrainingCenterSnapshotStore
import app.masary.feature.trainingcenter.domain.TrainingToolKey
import com.google.gson.Gson
import kotlinx.coroutines.flow.first

class DataStoreTrainingCenterSnapshotStore(
    private val dataStore: DataStore<Preferences>,
    private val gson: Gson = Gson(),
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
    private val maxAgeMillis: Long = DEFAULT_MAX_AGE_MILLIS,
) : TrainingCenterSnapshotStore {
    override suspend fun read(studentId: String, subjectVersionId: Int): StudentTrainingCenter? {
        if (studentId.isBlank() || subjectVersionId <= 0) return null
        val values = dataStore.data.first()
        if (values[OWNER] != studentId || values[SCHEMA_VERSION] != CURRENT_SCHEMA_VERSION) {
            clear()
            return null
        }
        val savedAt = values[savedAtKey(subjectVersionId)] ?: return null
        val age = nowEpochMillis() - savedAt
        if (age !in 0..maxAgeMillis) {
            evictSubject(subjectVersionId)
            return null
        }
        val json = values[payloadKey(subjectVersionId)] ?: return null
        val decoded = runCatching { gson.fromJson(json, StudentTrainingCenter::class.java) }.getOrNull()
        val validKeys = decoded?.tools?.map { it.key }?.toSet() == TrainingToolKey.ordered.toSet()
        if (
            decoded == null ||
            decoded.studentId != studentId ||
            decoded.subjectVersionId != subjectVersionId ||
            decoded.version.isBlank() ||
            !validKeys
        ) {
            evictSubject(subjectVersionId)
            return null
        }
        return decoded.copy(snapshot = TrainingCenterSnapshotMetadata(savedAt))
    }

    override suspend fun write(
        studentId: String,
        subjectVersionId: Int,
        data: StudentTrainingCenter,
    ) {
        require(studentId.isNotBlank()) { "Training center snapshot owner is required" }
        require(subjectVersionId > 0) { "Training center subject id must be positive" }
        require(data.studentId == studentId) { "Training center snapshot owner mismatch" }
        require(data.subjectVersionId == subjectVersionId) { "Training center subject id mismatch" }
        require(data.version.isNotBlank()) { "Training center snapshot version is required" }
        require(data.tools.map { it.key }.toSet() == TrainingToolKey.ordered.toSet()) {
            "Training center snapshot must contain the six stable tools"
        }

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

    private suspend fun evictSubject(subjectVersionId: Int) {
        dataStore.edit { preferences ->
            preferences.remove(payloadKey(subjectVersionId))
            preferences.remove(savedAtKey(subjectVersionId))
        }
    }

    private fun payloadKey(subjectVersionId: Int) =
        stringPreferencesKey("training_center_payload_$subjectVersionId")

    private fun savedAtKey(subjectVersionId: Int) =
        longPreferencesKey("training_center_saved_at_$subjectVersionId")

    private companion object {
        val OWNER = stringPreferencesKey("training_center_owner")
        val SCHEMA_VERSION = intPreferencesKey("training_center_schema_version")
        const val CURRENT_SCHEMA_VERSION = 1
        const val DEFAULT_MAX_AGE_MILLIS = 24L * 60L * 60L * 1_000L
    }
}
