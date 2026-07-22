package app.masary.feature.home.data

import app.masary.core.datastore.SessionManager
import app.masary.core.network.MasaryNetwork
import app.masary.feature.home.domain.HomeRepository

object HomeRepositoryFactory {
    fun create(
        sessionManager: SessionManager,
        baseUrl: String,
    ): HomeRepository = NetworkHomeRepository(
        homeApi = MasaryNetwork.studentHomeApi(baseUrl),
        authApi = MasaryNetwork.studentAuthApi(baseUrl),
        sessionManager = sessionManager,
    )
}
