package app.masary.feature.subjects.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import app.masary.feature.subjects.domain.StudentSubject
import app.masary.feature.subjects.domain.StudentSubjectsData
import app.masary.feature.subjects.domain.SubjectAccess
import app.masary.feature.subjects.domain.SubjectAccessStatus
import app.masary.feature.subjects.domain.SubjectLastActivity
import app.masary.feature.subjects.domain.SubjectMedia
import app.masary.feature.subjects.domain.SubjectProgress
import app.masary.feature.subjects.domain.SubjectsAcademicContext
import app.masary.feature.subjects.domain.SubjectsEmptyState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DataStoreSubjectsSnapshotStoreTest {
    @Test
    fun `snapshot is isolated by student owner`() = runTest {
        val store = DataStoreSubjectsSnapshotStore(MemorySubjectsPreferencesDataStore())
        store.write("42", subjects("42"))

        assertEquals("42", store.read("42")?.studentId)
        assertNull(store.read("7"))
        assertNull(store.read("42"))
    }

    @Test
    fun `confirmed subjects remain available after a long offline period`() = runTest {
        var now = 1_000L
        val store = DataStoreSubjectsSnapshotStore(
            MemorySubjectsPreferencesDataStore(),
            nowEpochMillis = { now },
        )
        store.write("42", subjects("42"))
        now += 30L * 24L * 60L * 60L * 1_000L

        assertEquals("42", store.read("42")?.studentId)
    }

    @Test
    fun `owner mismatch cannot be written`() = runTest {
        val store = DataStoreSubjectsSnapshotStore(MemorySubjectsPreferencesDataStore())
        var rejected = false
        try {
            store.write("7", subjects("42"))
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue(rejected)
    }

    private fun subjects(studentId: String) = StudentSubjectsData(
        studentId = studentId,
        version = "v1",
        generatedAt = "now",
        complete = true,
        academic = SubjectsAcademicContext(true, "الثالث الثانوي", "علمي", "تعز", "عدن", ""),
        subjects = listOf(
            StudentSubject(
                subjectVersionId = 12,
                name = "الرياضيات",
                hearts = 3,
                points = null,
                level = null,
                curriculumLabel = "منهج عدن",
                progress = SubjectProgress(false, null, "غير متاح"),
                media = SubjectMedia(false, null, "غير متاح"),
                access = SubjectAccess(false, SubjectAccessStatus.Unknown, "غير متاح"),
                lastActivity = SubjectLastActivity(false, null, "", "", "غير متاح"),
            ),
        ),
        empty = SubjectsEmptyState(false, ""),
    )
}

private class MemorySubjectsPreferencesDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = state
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(state.value).also { state.value = it }
}
