package app.masary.feature.auth.data

import app.masary.core.network.MasaryNetwork
import app.masary.feature.auth.domain.AuthRepository

object AuthRepositoryFactory {
    fun create(): AuthRepository = NetworkAuthRepository(MasaryNetwork.studentAuthApi())
}
