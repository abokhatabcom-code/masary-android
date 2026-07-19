package app.masary.core.security

import app.masary.core.models.auth.AuthTokens
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InMemoryTokenStoreTest {
    @Test fun `tokens can be saved and completely cleared in debug store`() = runTest {
        val store = InMemoryTokenStore()
        val tokens = AuthTokens("access", "refresh", 3600)

        store.save(tokens)
        assertEquals(tokens, store.read())
        store.clear()
        assertNull(store.read())
    }
}
