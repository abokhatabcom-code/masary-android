package app.masary.feature.auth.domain

import app.masary.core.models.auth.AuthenticatedStudent

data class AcademicOption(val id: Long, val name: String)
data class RegistrationCity(val id: Long, val name: String, val requiresSchool: Boolean)

private val EMAIL_PATTERN = Regex("^[A-Za-z0-9.!#$%&’*+/=?^_`{|}~-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+$")

data class RegistrationDraft(
    val fullName: String = "",
    val username: String = "",
    val phone: String = "",
    val email: String = "",
    val password: String = "",
    val passwordConfirmation: String = "",
    val gender: String = "",
    val personality: String = "",
    val city: RegistrationCity? = null,
    val school: AcademicOption? = null,
    val grade: AcademicOption? = null,
    val privacyAccepted: Boolean = false,
) {
    fun accountValid(): Boolean =
        fullName.isNotBlank() &&
            username.matches(Regex("[A-Za-z0-9_]{3,24}")) &&
            password.length >= 6 && password == passwordConfirmation &&
            gender in setOf("male", "female") &&
            (email.isBlank() || EMAIL_PATTERN.matches(email)) &&
            (personality.isBlank() || personality in setOf("explorer", "achiever", "calm"))

    fun academicValid(): Boolean = city != null && grade != null && (!city.requiresSchool || school != null)
    fun hasUserInput(): Boolean = listOf(fullName, username, phone, email, password, passwordConfirmation, gender, personality).any(String::isNotBlank) || city != null || school != null || grade != null
    fun clearedSensitive(): RegistrationDraft = copy(password = "", passwordConfirmation = "")
}

sealed interface LookupState<out T> {
    data object Loading : LookupState<Nothing>
    data class Ready<T>(val items: List<T>) : LookupState<T>
    data object Empty : LookupState<Nothing>
    data object Error : LookupState<Nothing>
}

enum class RegistrationFailureKind { VALIDATION, CONFLICT, RETRYABLE, UNKNOWN }
class RegistrationException(val kind: RegistrationFailureKind) : Exception()

interface RegistrationRepository {
    suspend fun cities(): Result<List<RegistrationCity>>
    suspend fun grades(): Result<List<AcademicOption>>
    suspend fun schools(cityId: Long): Result<List<AcademicOption>>
    suspend fun register(draft: RegistrationDraft, deviceName: String, idempotencyKey: String): Result<AuthenticatedStudent>
}
