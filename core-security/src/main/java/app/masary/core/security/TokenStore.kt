package app.masary.core.security

import app.masary.core.models.auth.AuthTokens

/** Keeps authentication tokens outside plain Preferences storage. */
interface TokenStore {
    suspend fun read(): AuthTokens?
    suspend fun save(tokens: AuthTokens)
    suspend fun clear()
}
