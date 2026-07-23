package app.masary.feature.notifications
import org.junit.Assert.assertEquals
import org.junit.Test
class NotificationDestinationPolicyTest {
 @Test fun `allowed destination requires session`() { assertEquals("subjects",NotificationDestinationPolicy.resolve("subjects",true,false)); assertEquals("home",NotificationDestinationPolicy.resolve("subjects",false,false)) }
 @Test fun `unknown destination falls back home`() { assertEquals("home",NotificationDestinationPolicy.resolve("https://evil.example",true,false)) }
 @Test fun `active educational test is never interrupted`() { assertEquals("home",NotificationDestinationPolicy.resolve("subjects",true,true)) }
}
