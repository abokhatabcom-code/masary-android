package app.masary.feature.auth.data

import app.masary.core.models.auth.*
import app.masary.core.network.auth.*
import app.masary.feature.auth.domain.*
import java.io.IOException
import retrofit2.HttpException

class NetworkRegistrationRepository(private val api: StudentAuthApi) : RegistrationRepository {
    override suspend fun cities() = lookup { api.cities().data?.cities?.map { RegistrationCity(it.id, it.name, it.requiresSchool) } }
    override suspend fun grades() = lookup { api.grades().data?.grades?.map { AcademicOption(it.id, it.name) } }
    override suspend fun schools(cityId: Long) = lookup { api.schools(cityId).data?.schools?.map { AcademicOption(it.id, it.name) } }

    override suspend fun register(draft: RegistrationDraft, deviceName: String, idempotencyKey: String) = runCatching {
        val response = api.register(
            idempotencyKey,
            StudentRegistrationRequestDto(draft.fullName.trim(), draft.username.trim(), draft.phone.trim().ifBlank { null }, draft.email.trim().ifBlank { null }, draft.password, draft.passwordConfirmation, draft.gender, draft.personality.ifBlank { null }, requireNotNull(draft.city).id, draft.school?.id, requireNotNull(draft.grade).id, draft.privacyAccepted, deviceName),
        )
        val data = response.data ?: throw RegistrationException(
            if (response.error?.code == "username_taken" || response.error?.code == "idempotency_key_conflict") RegistrationFailureKind.CONFLICT else RegistrationFailureKind.VALIDATION,
        )
        AuthenticatedStudent(Student(data.student.id, data.student.username, data.student.displayName), AuthTokens(data.accessToken, data.refreshToken, data.expiresIn))
    }.recoverCatching { throw mapRegistrationFailure(it) }

    private suspend fun <T> lookup(block: suspend () -> List<T>?): Result<List<T>> = runCatching { block() ?: error("invalid response") }
    private fun mapRegistrationFailure(error: Throwable): Throwable = when (error) {
        is RegistrationException -> error
        is IOException -> RegistrationException(RegistrationFailureKind.RETRYABLE)
        is HttpException -> when (error.code()) {
            409 -> RegistrationException(RegistrationFailureKind.CONFLICT)
            400, 422 -> RegistrationException(RegistrationFailureKind.VALIDATION)
            429, in 500..599 -> RegistrationException(RegistrationFailureKind.RETRYABLE)
            else -> RegistrationException(RegistrationFailureKind.UNKNOWN)
        }
        else -> RegistrationException(RegistrationFailureKind.UNKNOWN)
    }
}
