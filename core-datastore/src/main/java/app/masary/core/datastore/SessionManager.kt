package app.masary.core.datastore

import kotlinx.coroutines.flow.Flow

data class StudentSession(val studentId: String, val studentName: String, val accessToken: String)

interface SessionManager {
    val session: Flow<StudentSession?>
    suspend fun save(session: StudentSession)
    suspend fun clear()
}
