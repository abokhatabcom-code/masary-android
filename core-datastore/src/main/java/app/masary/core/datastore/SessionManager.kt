package app.masary.core.datastore

import app.masary.core.models.auth.AuthenticatedStudent
import app.masary.core.models.auth.AuthTokens
import app.masary.core.models.auth.StudentSession
import kotlinx.coroutines.flow.Flow

interface SessionManager {
    val session: Flow<StudentSession?>
    suspend fun save(authenticatedStudent: AuthenticatedStudent)
    suspend fun readTokens(): AuthTokens? = null
    suspend fun updateTokens(tokens: AuthTokens) = Unit
    suspend fun clear()
}
