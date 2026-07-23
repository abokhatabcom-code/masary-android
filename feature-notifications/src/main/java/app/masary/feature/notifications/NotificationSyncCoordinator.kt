package app.masary.feature.notifications
import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

object NotificationSyncCoordinator {
    const val MIN_BACKOFF_SECONDS = 30L
    private const val PREFS="notification_runtime_v1"
    fun configure(context:Context,baseUrl:String,hasSession:Boolean) { context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString("base_url",baseUrl).putBoolean("has_session",hasSession).apply() }
    fun updatePermission(context:Context,status:NotificationPermissionState) { context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString("permission",status.name).apply() }
    fun scheduleRegistration(context:Context) = enqueue(context,"register",ExistingWorkPolicy.KEEP)
    fun scheduleUnregister(context:Context,accessToken:String,refreshToken:String):Boolean {
        val saved=LogoutPreparation { SecurePendingTokenStore(context,"logout_session").write(it) }.save(PendingLogout(accessToken,refreshToken))
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putBoolean("logout_pending_persisted",saved).commit()
        if(!saved)return false
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putBoolean("has_session",false).apply()
        enqueue(context,"unregister",ExistingWorkPolicy.KEEP)
        return true
    }
    private fun enqueue(context:Context,action:String,policy:ExistingWorkPolicy) {
        val request=OneTimeWorkRequestBuilder<NotificationSyncWorker>().setInputData(workDataOf("action" to action))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,MIN_BACKOFF_SECONDS,TimeUnit.SECONDS).build()
        WorkManager.getInstance(context).enqueueUniqueWork("notifications-v1-$action",policy,request)
    }
}
data class PendingLogout(val accessToken:String,val refreshToken:String) {
    fun rotated(accessToken:String,refreshToken:String)=PendingLogout(accessToken,refreshToken)
    override fun toString()="PendingLogout(accessToken=[REDACTED], refreshToken=[REDACTED])"
}
