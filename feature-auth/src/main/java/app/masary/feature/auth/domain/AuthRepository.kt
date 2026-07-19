package app.masary.feature.auth.domain

import app.masary.core.models.auth.AuthenticatedStudent

fun interface AuthRepository {
    suspend fun login(username: String, password: CharArray, deviceName: String): Result<AuthenticatedStudent>
}
