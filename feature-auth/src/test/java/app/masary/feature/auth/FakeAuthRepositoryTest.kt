package app.masary.feature.auth

import app.masary.feature.auth.data.AuthRepositoryFactory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeAuthRepositoryTest {
    @Test fun `only fixed demo account succeeds`() = runTest {
        val repository = AuthRepositoryFactory.create()
        assertTrue(repository.login(AuthRepositoryFactory.DEMO_USERNAME, AuthRepositoryFactory.DEMO_PASSWORD.toCharArray(), "test").isSuccess)
        assertTrue(repository.login(AuthRepositoryFactory.DEMO_USERNAME, "incorrect".toCharArray(), "test").isFailure)
        assertTrue(repository.login("another", AuthRepositoryFactory.DEMO_PASSWORD.toCharArray(), "test").isFailure)
    }
}
