package app.masary.feature.notifications
import org.junit.Assert.assertEquals
import org.junit.Test
class NotificationSyncPolicyTest { @Test fun `network retry uses WorkManager minimum exponential backoff`() { assertEquals(30L,NotificationSyncCoordinator.MIN_BACKOFF_SECONDS) } }
