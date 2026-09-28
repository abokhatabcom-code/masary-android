package app.masary.feature.questionsession.data

import app.masary.core.datastore.SessionManager
import app.masary.core.local.StudentLocalStore
import app.masary.core.network.MasaryNetwork
import app.masary.feature.questionsession.domain.QuestionSessionRepository

object QuestionSessionRepositoryFactory {
    fun create(
        sessionManager: SessionManager,
        localStore: StudentLocalStore,
        baseUrl: String,
    ): QuestionSessionRepository = NetworkQuestionSessionRepository(
        questionApi = MasaryNetwork.studentQuestionSessionApi(baseUrl),
        authApi = MasaryNetwork.studentAuthApi(baseUrl),
        sessionManager = sessionManager,
        localStore = localStore,
    )
}
