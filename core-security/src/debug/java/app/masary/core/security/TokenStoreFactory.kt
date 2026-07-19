package app.masary.core.security

import app.masary.core.models.auth.AuthTokens

/** Debug-only volatile storage: tokens disappear with the application process. */
object TokenStoreFactory {
    fun create(): TokenStore = InMemoryTokenStore()
}

class InMemoryTokenStore : TokenStore {
    private var tokens: AuthTokens? = null

    override suspend fun read(): AuthTokens? = tokens
    override suspend fun save(tokens: AuthTokens) { this.tokens = tokens }
    override suspend fun clear() { tokens = null }
}
