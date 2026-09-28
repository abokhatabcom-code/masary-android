package app.masary.feature.subject.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import app.masary.feature.subject.domain.StudentSubjectPage
import app.masary.feature.subject.domain.SubjectAccess
import app.masary.feature.subject.domain.SubjectAccessStatus
import app.masary.feature.subject.domain.SubjectActionAvailability
import app.masary.feature.subject.domain.SubjectActions
import app.masary.feature.subject.domain.SubjectContentSummary
import app.masary.feature.subject.domain.SubjectHearts
import app.masary.feature.subject.domain.SubjectIdentity
import app.masary.feature.subject.domain.SubjectIntValue
import app.masary.feature.subject.domain.SubjectLastActivity
import app.masary.feature.subject.domain.SubjectMedia
import app.masary.feature.subject.domain.SubjectProgressValue
import app.masary.feature.subject.domain.SubjectRestoreTime
import app.masary.feature.subject.domain.SubjectStructureMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DataStoreSubjectSnapshotStoreTest {
    @Test
    fun `stores separate subject snapshots for one student`() = runTest {
        val store = DataStoreSubjectSnapshotStore(MemorySubjectPreferencesDataStore())
        store.write("42", 12, subject("42", 12, "v12"))
        store.write("42", 13, subject("42", 13, "v13"))

        assertEquals("v12", store.read("42", 12)?.version)
        assertEquals("v13", store.read("42", 13)?.version)
    }

    @Test
    fun `owner mismatch evicts all subject snapshots`() = runTest {
        val store = DataStoreSubjectSnapshotStore(MemorySubjectPreferencesDataStore())
        store.write("42", 12, subject("42", 12, "v12"))

        assertNull(store.read("7", 12))
        assertNull(store.read("42", 12))
    }

    @Test
    fun `subject mismatch cannot be written`() = runTest {
        val store = DataStoreSubjectSnapshotStore(MemorySubjectPreferencesDataStore())
        var rejected = false
        try {
            store.write("42", 13, subject("42", 12, "v12"))
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue(rejected)
    }

    @Test
    fun `confirmed subject snapshots remain available after a long offline period`() = runTest {
        var now = 1_000L
        val store = DataStoreSubjectSnapshotStore(
            MemorySubjectPreferencesDataStore(),
            nowEpochMillis = { now },
        )
        store.write("42", 12, subject("42", 12, "v12"))
        now += 15L * 24L * 60L * 60L * 1_000L
        store.write("42", 13, subject("42", 13, "v13"))
        now += 15L * 24L * 60L * 60L * 1_000L

        assertEquals("v12", store.read("42", 12)?.version)
        assertEquals("v13", store.read("42", 13)?.version)
    }

    private fun subject(studentId: String, subjectVersionId: Int, version: String) = StudentSubjectPage(
        studentId = studentId,
        subjectVersionId = subjectVersionId,
        generatedAt = "now",
        identity = SubjectIdentity("الرياضيات", "منهج عدن", "gov", SubjectMedia(false, null, "")),
        points = SubjectIntValue(true, 120, ""),
        level = SubjectIntValue(false, null, "غير متاح"),
        progress = SubjectProgressValue(false, null, "غير متاح"),
        hearts = SubjectHearts(2, 3, SubjectRestoreTime(false, null, "غير متاح")),
        access = SubjectAccess(false, SubjectAccessStatus.Unknown, "غير متاح"),
        content = SubjectContentSummary(SubjectStructureMode.Units, false, emptyList(), false, "لاحقًا"),
        lastActivity = SubjectLastActivity(false, null, "", "", SubjectActionAvailability(false, ""), ""),
        actions = SubjectActions(SubjectActionAvailability(false, "لاحقًا")),
        version = version,
    )
}

private class MemorySubjectPreferencesDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = state
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(state.value).also { state.value = it }
}
