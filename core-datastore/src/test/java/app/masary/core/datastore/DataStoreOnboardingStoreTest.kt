package app.masary.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DataStoreOnboardingStoreTest {
    @Test fun `onboarding is shown once and can be explicitly reset`() = runTest {
        val store = store(this)
        assertFalse(store.isCompleted.first())

        store.markCompleted()
        assertTrue(store.isCompleted.first())

        store.reset()
        assertFalse(store.isCompleted.first())
    }

    private fun store(scope: TestScope): DataStoreOnboardingStore {
        val file = File.createTempFile("onboarding", ".preferences_pb").also { it.delete() }
        val dataStore = PreferenceDataStoreFactory.create(scope = scope.backgroundScope) { file }
        return DataStoreOnboardingStore(dataStore)
    }
}
