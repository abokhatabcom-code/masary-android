package app.masary.feature.notifications
import org.junit.Assert.*
import org.junit.Test
class FirebaseInitializerTest {
 @Test fun `configuration must be complete`() { assertTrue(FirebaseInitializer.configurationComplete("p","a","k","s")); assertFalse(FirebaseInitializer.configurationComplete("p","a","k","")) }
}
