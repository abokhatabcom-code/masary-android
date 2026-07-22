package app.masary.feature.auth.data

import app.masary.core.network.MasaryNetwork
import app.masary.feature.auth.domain.AuthRepository
import app.masary.feature.auth.domain.RegistrationRepository

object AuthRepositoryFactory {
    fun create(baseUrl: String): AuthRepository =
        NetworkAuthRepository(MasaryNetwork.studentAuthApi(baseUrl))
    fun createRegistration(baseUrl: String): RegistrationRepository =
        NetworkRegistrationRepository(MasaryNetwork.studentAuthApi(baseUrl))
}
