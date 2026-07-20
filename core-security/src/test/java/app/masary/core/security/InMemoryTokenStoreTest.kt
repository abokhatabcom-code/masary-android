package app.masary.core.security

import app.masary.core.models.auth.AuthTokens
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthTokensTest {
    @Test fun `access token refresh window is enforced`() {
        val tokens = AuthTokens(
            accessToken = "access",
            refreshToken = "refresh",
            expiresInSeconds = 900,
            accessTokenExpiresAtEpochSeconds = 1_000,
        )

        assertFalse(tokens.accessTokenNeedsRefresh(nowEpochSeconds = 900, refreshBeforeSeconds = 60))
        assertTrue(tokens.accessTokenNeedsRefresh(nowEpochSeconds = 940, refreshBeforeSeconds = 60))
    }
}
