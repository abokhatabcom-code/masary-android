package app.masary.feature.home.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import app.masary.feature.home.domain.HomeStudent
import app.masary.feature.home.domain.StudentHomeData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DataStoreHomeSnapshotStoreTest {
    @Test
    fun `snapshot is isolated by owner and invalid owner evicts payload`() = runTest {
        val store = DataStoreHomeSnapshotStore(MemoryPreferencesDataStore())
        store.write("42", home("42"))

        assertEquals("42", store.read("42")?.student?.id)
        assertNull(store.read("7"))
        assertNull(store.read("42"))
    }

    @Test
    fun `expired snapshot is evicted`() = runTest {
        var now = 1_000L
        val store = DataStoreHomeSnapshotStore(
            MemoryPreferencesDataStore(),
            nowEpochMillis = { now },
            maxAgeMillis = 100L,
        )
        store.write("42", home("42"))

        now = 1_101L
        assertNull(store.read("42"))
        assertNull(store.read("42"))
    }

    @Test
    fun `clear removes the snapshot`() = runTest {
        val store = DataStoreHomeSnapshotStore(MemoryPreferencesDataStore())
        store.write("42", home("42"))
        store.clear()
        assertNull(store.read("42"))
    }

    private fun home(studentId: String): StudentHomeData {
        val fixture = com.google.gson.Gson().fromJson(
            javaClass.classLoader!!.getResource("home-snapshot.json")?.readText()
                ?: error("Missing fixture"),
            StudentHomeData::class.java,
        )
        return fixture.copy(student = HomeStudent(studentId, "student", "طالب", null))
    }
}

private class MemoryPreferencesDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = state
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(state.value).also { state.value = it }
}
