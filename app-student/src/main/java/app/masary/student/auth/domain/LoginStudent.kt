package app.masary.student.auth.domain

class LoginStudent(private val repository: StudentAuthRepository) {
    operator fun invoke(studentId: String, password: CharArray): LoginResult {
        val normalizedStudentId = studentId.trim()
        if (normalizedStudentId.isEmpty()) {
            password.fill('\u0000')
            return LoginResult.InvalidStudentId
        }
        if (password.isEmpty()) return LoginResult.InvalidPassword

        return try {
            if (repository.authenticate(normalizedStudentId, password)) {
                LoginResult.Success
            } else {
                LoginResult.Rejected
            }
        } finally {
            password.fill('\u0000')
        }
    }
}
