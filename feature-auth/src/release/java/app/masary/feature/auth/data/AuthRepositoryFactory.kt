package app.masary.feature.auth.data

import app.masary.core.network.MasaryNetwork
import app.masary.feature.auth.domain.AuthRepository

object AuthRepositoryFactory {
    fun create(baseUrl: String): AuthRepository =
        NetworkAuthRepository(MasaryNetwork.studentAuthApi(baseUrl))
}
