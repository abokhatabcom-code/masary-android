package app.masary.feature.notifications
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test
class SyncFailurePolicyTest {
 @Test fun `client errors are permanent`() { listOf(400,401,403,404,409,422).forEach{assertEquals(SyncDecision.PermanentFailure,SyncFailurePolicy.classify(it))} }
 @Test fun `only temporary network errors retry`() { listOf(408,429,500,503).forEach{assertEquals(SyncDecision.Retry,SyncFailurePolicy.classify(it))};assertEquals(SyncDecision.Retry,SyncFailurePolicy.classify(error=IOException())) ;assertEquals(SyncDecision.PermanentFailure,SyncFailurePolicy.classify(error=IllegalArgumentException())) }
 @Test fun `attempts are bounded`() { assertEquals(SyncDecision.PermanentFailure,SyncFailurePolicy.classify(503,attempt=SyncFailurePolicy.MAX_ATTEMPTS-1)) }
 @Test fun `logout payload redacts rotated credentials`() { val p=PendingLogout("access-secret","refresh-secret");assertFalse(p.toString().contains("access-secret"));assertFalse(p.toString().contains("refresh-secret")) }
 @Test fun `refresh rotation replaces both credentials used by logout`() { val rotated=PendingLogout("expired-access","old-refresh").rotated("new-access","new-refresh");assertEquals("new-access",rotated.accessToken);assertEquals("new-refresh",rotated.refreshToken);assertNotEquals("old-refresh",rotated.refreshToken) }
}
