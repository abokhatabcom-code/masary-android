package app.masary.core.security

import app.masary.core.models.auth.AuthTokens

/** Release builds reject tokens until Android Keystore-backed persistence is provided. */
object TokenStoreFactory {
    fun create(): TokenStore = RejectingTokenStore
}

private object RejectingTokenStore : TokenStore {
    override suspend fun read(): AuthTokens? = null
    override suspend fun save(tokens: AuthTokens): Unit =
        error("Secure token storage is unavailable")
    override suspend fun clear() = Unit
}
