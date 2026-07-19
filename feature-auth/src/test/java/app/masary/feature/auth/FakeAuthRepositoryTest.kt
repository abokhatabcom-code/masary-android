package app.masary.feature.auth

import app.masary.feature.auth.data.AuthRepositoryFactory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeAuthRepositoryTest {
    @Test fun `only fixed demo account succeeds`() = runTest {
        val repository = AuthRepositoryFactory.create()
        assertTrue(repository.login(AuthRepositoryFactory.DEMO_STUDENT_ID, AuthRepositoryFactory.DEMO_PASSWORD.toCharArray()).isSuccess)
        assertTrue(repository.login(AuthRepositoryFactory.DEMO_STUDENT_ID, "incorrect".toCharArray()).isFailure)
        assertTrue(repository.login("another", AuthRepositoryFactory.DEMO_PASSWORD.toCharArray()).isFailure)
    }
}
