package app.masary.feature.notifications
import org.junit.Assert.*
import org.junit.Test
class NotificationPendingIntentPolicyTest { @Test fun `two messages retain distinct pending intents`() { val first=NotificationPendingIntentPolicy.requestCode("message-1","subjects");val second=NotificationPendingIntentPolicy.requestCode("message-2","profile");assertNotEquals(first,second) } }
