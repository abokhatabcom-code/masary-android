package app.masary.feature.questionsession.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

object QuestionAnswerSyncCoordinator {
    const val MIN_BACKOFF_SECONDS = 30L
    private const val UNIQUE_WORK = "question-answer-sync-v1"
    internal const val KEY_BASE_URL = "base_url"

    fun schedule(context: Context, baseUrl: String) {
        val request = OneTimeWorkRequestBuilder<QuestionAnswerSyncWorker>()
            .setInputData(workDataOf(KEY_BASE_URL to baseUrl))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                MIN_BACKOFF_SECONDS,
                TimeUnit.SECONDS,
            )
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            UNIQUE_WORK,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }
}
