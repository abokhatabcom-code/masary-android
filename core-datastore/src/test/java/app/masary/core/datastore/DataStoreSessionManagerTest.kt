package app.masary.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.masary.core.models.auth.AuthenticatedStudent
import app.masary.core.models.auth.AuthTokens
import app.masary.core.models.auth.Student
import app.masary.core.models.auth.StudentSession
import app.masary.core.security.TokenStore
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DataStoreSessionManagerTest {
    @Test fun `save persists only student session and delegates tokens`() = runTest {
        val tokenStore = RecordingTokenStore()
        val manager = manager(tokenStore, this)
        val authenticated = authenticatedStudent()

        manager.save(authenticated)

        assertEquals(authenticated.tokens, tokenStore.tokens)
        assertEquals(StudentSession("7", "student", "سارة"), manager.session.firstValue())
    }

    @Test fun `clear removes local session and both tokens`() = runTest {
        val tokenStore = RecordingTokenStore()
        val manager = manager(tokenStore, this)
        manager.save(authenticatedStudent())

        manager.clear()

        assertNull(tokenStore.tokens)
        assertNull(manager.session.firstValue())
    }

    @Test fun `student data without tokens expires and is removed`() = runTest {
        val tokenStore = RecordingTokenStore()
        val manager = manager(tokenStore, this)
        manager.save(authenticatedStudent())
        tokenStore.clear()

        assertNull(manager.session.firstValue())

        // Restoring tokens later must not resurrect the stale local student record.
        tokenStore.save(AuthTokens("new-access", "new-refresh", 3600))
        assertNull(manager.session.firstValue())
    }

    @Test fun `clear still removes local data when token clearing fails`() = runTest {
        val tokenStore = RecordingTokenStore()
        val manager = manager(tokenStore, this)
        manager.save(authenticatedStudent())
        tokenStore.failOnClear = true

        runCatching { manager.clear() }
        tokenStore.failOnClear = false
        tokenStore.tokens = AuthTokens("access", "refresh", 3600)

        assertNull(manager.session.firstValue())
    }

    private fun manager(tokenStore: TokenStore, scope: TestScope): DataStoreSessionManager {
        val file = File.createTempFile("session", ".preferences_pb").also { it.delete() }
        val dataStore = PreferenceDataStoreFactory.create(scope = scope.backgroundScope) { file }
        return DataStoreSessionManager(dataStore, tokenStore)
    }

    private fun authenticatedStudent() = AuthenticatedStudent(
        Student("7", "student", "سارة"),
        AuthTokens("access", "refresh", 3600),
    )
}

private suspend fun <T> kotlinx.coroutines.flow.Flow<T>.firstValue(): T = first()

private class RecordingTokenStore : TokenStore {
    var tokens: AuthTokens? = null
    var failOnClear = false
    override suspend fun read() = tokens
    override suspend fun save(tokens: AuthTokens) { this.tokens = tokens }
    override suspend fun clear() {
        if (failOnClear) error("token clear failed")
        tokens = null
    }
}
