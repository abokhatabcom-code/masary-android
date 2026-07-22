package app.masary.feature.notifications
import org.junit.Assert.*
import org.junit.Test
class SyncFailurePolicyTest {
 @Test fun `401 and 422 are permanent`() { assertFalse(SyncFailurePolicy.shouldRetry(401));assertFalse(SyncFailurePolicy.shouldRetry(422)) }
 @Test fun `network rate limit and server errors retry`() { assertTrue(SyncFailurePolicy.shouldRetry(null));assertTrue(SyncFailurePolicy.shouldRetry(429));assertTrue(SyncFailurePolicy.shouldRetry(503)) }
 @Test fun `logout payload never leaks tokens`() { val p=PendingLogout("access-secret","refresh-secret");assertFalse(p.toString().contains("access-secret"));assertFalse(p.toString().contains("refresh-secret")) }
}
