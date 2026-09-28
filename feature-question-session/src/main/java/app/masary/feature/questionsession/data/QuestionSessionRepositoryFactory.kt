package app.masary.feature.questionsession.data

import android.content.Context
import app.masary.core.datastore.SessionManager
import app.masary.core.local.StudentLocalStore
import app.masary.core.network.MasaryNetwork
import app.masary.feature.questionsession.domain.QuestionSessionRepository
import app.masary.feature.questionsession.sync.QuestionAnswerSyncCoordinator

object QuestionSessionRepositoryFactory {
    fun create(
        context: Context,
        sessionManager: SessionManager,
        localStore: StudentLocalStore,
        baseUrl: String,
    ): QuestionSessionRepository = NetworkQuestionSessionRepository(
        questionApi = MasaryNetwork.studentQuestionSessionApi(baseUrl),
        authApi = MasaryNetwork.studentAuthApi(baseUrl),
        sessionManager = sessionManager,
        localStore = localStore,
        onPendingAnswerSaved = {
            QuestionAnswerSyncCoordinator.schedule(context.applicationContext, baseUrl)
        },
    )
}
