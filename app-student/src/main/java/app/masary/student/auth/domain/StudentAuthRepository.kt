package app.masary.student.auth.domain

fun interface StudentAuthRepository {
    fun authenticate(studentId: String, password: CharArray): Boolean
}
