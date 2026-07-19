package app.masary.student.auth.data

import app.masary.student.auth.domain.StudentAuthRepository

/** A local-only authentication seam used by the debug variant. */
object StudentAuthFactory {
    fun create(): StudentAuthRepository = StudentAuthRepository { _, password ->
        password.isNotEmpty()
    }
}
