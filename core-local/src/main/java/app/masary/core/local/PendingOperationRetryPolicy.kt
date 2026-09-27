package app.masary.core.local

object PendingOperationRetryPolicy {
    private const val BASE_DELAY_MILLIS = 5_000L
    private const val MAX_DELAY_MILLIS = 15L * 60L * 1_000L

    fun nextDelayMillis(attemptCount: Int): Long {
        val exponent = attemptCount.coerceIn(0, 16)
        val multiplier = 1L shl exponent
        return (BASE_DELAY_MILLIS * multiplier).coerceAtMost(MAX_DELAY_MILLIS)
    }
}
