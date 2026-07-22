package app.masary.feature.notifications
import org.junit.Assert.*
import org.junit.Test
class NotificationPolicyTest {
 @Test fun `permission is runtime only from Android 13`() { assertFalse(NotificationPolicy.requiresRuntimePermission(32)); assertTrue(NotificationPolicy.requiresRuntimePermission(33)) }
 @Test fun `channel id remains stable`() { assertEquals("masary_learning_v1", NotificationPolicy.CHANNEL_LEARNING) }
}
