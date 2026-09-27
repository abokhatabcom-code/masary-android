package app.masary.core.local

import org.junit.Assert.assertEquals
import org.junit.Test

class PendingOperationRetryPolicyTest {
    @Test
    fun `retry delay grows and is capped`() {
        assertEquals(5_000L, PendingOperationRetryPolicy.nextDelayMillis(0))
        assertEquals(10_000L, PendingOperationRetryPolicy.nextDelayMillis(1))
        assertEquals(20_000L, PendingOperationRetryPolicy.nextDelayMillis(2))
        assertEquals(15L * 60L * 1_000L, PendingOperationRetryPolicy.nextDelayMillis(20))
    }
}
