package app.masary.student.auth.data

import app.masary.student.auth.domain.StudentAuthRepository

/** Release builds must use a real API implementation when one becomes available. */
object StudentAuthFactory {
    fun create(): StudentAuthRepository = StudentAuthRepository { _, _ -> false }
}
