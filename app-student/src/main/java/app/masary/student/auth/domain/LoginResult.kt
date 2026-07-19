package app.masary.student.auth.domain

sealed interface LoginResult {
    data object Success : LoginResult
    data object InvalidStudentId : LoginResult
    data object InvalidPassword : LoginResult
    data object Rejected : LoginResult
}
