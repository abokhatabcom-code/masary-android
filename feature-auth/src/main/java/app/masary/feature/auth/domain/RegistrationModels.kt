package app.masary.feature.auth.domain

import app.masary.core.models.auth.AuthenticatedStudent

data class AcademicOption(val id: Long, val name: String)
data class RegistrationCity(val id: Long, val name: String, val requiresSchool: Boolean)
data class RegistrationDraft(
    val fullName: String = "", val username: String = "", val phone: String = "", val email: String = "",
    val password: String = "", val passwordConfirmation: String = "", val gender: String = "",
    val personality: String = "", val city: RegistrationCity? = null, val school: AcademicOption? = null,
    val grade: AcademicOption? = null, val privacyAccepted: Boolean = false,
) {
    fun accountValid() = fullName.isNotBlank() && username.matches(Regex("[A-Za-z0-9_]{3,24}")) && password.length >= 6 && password == passwordConfirmation && gender in setOf("male", "female")
    fun academicValid() = city != null && grade != null && (!city.requiresSchool || school != null)
    fun clearedSensitive() = copy(password = "", passwordConfirmation = "")
}
interface RegistrationRepository {
    suspend fun cities(): Result<List<RegistrationCity>>
    suspend fun grades(): Result<List<AcademicOption>>
    suspend fun schools(cityId: Long): Result<List<AcademicOption>>
    suspend fun register(draft: RegistrationDraft, deviceName: String, idempotencyKey: String): Result<AuthenticatedStudent>
}
