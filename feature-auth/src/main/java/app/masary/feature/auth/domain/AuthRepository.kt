package app.masary.feature.auth.domain

import app.masary.core.datastore.StudentSession

fun interface AuthRepository {
    suspend fun login(studentId: String, password: CharArray): Result<StudentSession>
}
