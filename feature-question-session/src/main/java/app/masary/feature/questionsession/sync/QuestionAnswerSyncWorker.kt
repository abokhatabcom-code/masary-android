package app.masary.feature.questionsession.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.masary.core.datastore.SessionManager
import app.masary.core.local.MasaryLocalDatabase
import app.masary.core.local.RoomStudentLocalStore
import app.masary.core.models.auth.AuthenticatedStudent
import app.masary.core.models.auth.AuthTokens
import app.masary.core.models.auth.StudentSession
import app.masary.core.network.MasaryNetwork
import app.masary.core.network.auth.StudentRefreshRequestDto
import app.masary.core.security.TokenStore
import app.masary.core.security.TokenStoreFactory
import app.masary.feature.questionsession.data.NetworkQuestionSessionRepository
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import retrofit2.HttpException

class QuestionAnswerSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val baseUrl = inputData.getString(QuestionAnswerSyncCoordinator.KEY_BASE_URL)
            ?.takeIf(String::isNotBlank)
            ?: return Result.failure()

        val tokenStore = TokenStoreFactory.create(applicationContext)
        var tokens = tokenStore.read() ?: return Result.success()
        val authApi = MasaryNetwork.studentAuthApi(baseUrl)

        return try {
            if (tokens.accessTokenNeedsRefresh()) {
                tokens = refresh(tokens, tokenStore, baseUrl)
            }

            val student = try {
                authApi.me("Bearer ${tokens.accessToken}")
            } catch (error: HttpException) {
                if (error.code() != 401) throw error
                tokens = refresh(tokens, tokenStore, baseUrl)
                authApi.me("Bearer ${tokens.accessToken}")
            }

            val studentData = student.data?.student
            if (!student.success || studentData == null || studentData.id.isBlank()) {
                return Result.failure()
            }

            val sessionManager = WorkerSessionManager(
                tokenStore = tokenStore,
                session = StudentSession(
                    id = studentData.id,
                    username = studentData.username,
                    displayName = studentData.displayName,
                ),
            )
            val localStore = RoomStudentLocalStore(MasaryLocalDatabase.get(applicationContext))
            val repository = NetworkQuestionSessionRepository(
                questionApi = MasaryNetwork.studentQuestionSessionApi(baseUrl),
                authApi = authApi,
                sessionManager = sessionManager,
                localStore = localStore,
            )

            val summary = repository.syncPendingAnswers().getOrElse { error ->
                return classifyFailure(error)
            }
            if (summary.retryScheduled > 0) {
                return Result.retry()
            }

            // A failed answer can be waiting for its local backoff window and therefore
            // not appear in the ready batch yet. Never try to finish while any answer
            // operation remains outstanding.
            if (localStore.observeOutstandingOperations(studentData.id).first() > 0) {
                return Result.retry()
            }

            var completed = localStore.readOldestCompletedLocalQuestionSession(studentData.id)
            var finalized = 0
            while (completed != null && finalized < 10) {
                repository.finishSession(completed.sessionId).getOrElse { error ->
                    return classifyFailure(error)
                }
                finalized += 1
                completed = localStore.readOldestCompletedLocalQuestionSession(studentData.id)
            }
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            classifyFailure(error)
        }
    }

    private suspend fun refresh(
        current: AuthTokens,
        tokenStore: TokenStore,
        baseUrl: String,
    ): AuthTokens {
        val response = MasaryNetwork.studentAuthApi(baseUrl)
            .refresh(StudentRefreshRequestDto(current.refreshToken))
        val data = response.data
        if (!response.success || data == null) {
            throw IllegalStateException("Unable to refresh student session.")
        }
        return AuthTokens(
            accessToken = data.accessToken,
            refreshToken = data.refreshToken,
            expiresInSeconds = data.expiresIn,
        ).also { tokenStore.save(it) }
    }

    private fun classifyFailure(error: Throwable): Result = when (error) {
        is IOException -> Result.retry()
        is HttpException -> when {
            error.code() == 408 || error.code() == 425 || error.code() == 429 -> Result.retry()
            error.code() >= 500 -> Result.retry()
            else -> Result.failure()
        }
        else -> Result.failure()
    }
}

private class WorkerSessionManager(
    private val tokenStore: TokenStore,
    session: StudentSession,
) : SessionManager {
    override val session: Flow<StudentSession?> = flowOf(session)

    override suspend fun save(authenticatedStudent: AuthenticatedStudent) {
        tokenStore.save(authenticatedStudent.tokens)
    }

    override suspend fun readTokens(): AuthTokens? = tokenStore.read()

    override suspend fun updateTokens(tokens: AuthTokens) {
        tokenStore.save(tokens)
    }

    override suspend fun clear() {
        tokenStore.clear()
    }
}
