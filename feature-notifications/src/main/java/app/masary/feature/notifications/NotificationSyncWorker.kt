package app.masary.feature.notifications
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.masary.core.network.MasaryNetwork
import app.masary.core.network.notifications.PushInstallationRequest
import app.masary.core.security.TokenStoreFactory
import java.util.Locale
import java.util.TimeZone

class NotificationSyncWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
 override suspend fun doWork():Result {
    val prefs=applicationContext.getSharedPreferences("notification_runtime_v1",Context.MODE_PRIVATE)
    val action=inputData.getString("action") ?: return Result.failure()
    val baseUrl=prefs.getString("base_url",null) ?: return Result.failure()
    val fcm=SecurePendingTokenStore(applicationContext).read()
    val logoutStore=SecurePendingTokenStore(applicationContext,"logout_access")
    val access=if(action=="unregister") logoutStore.read() else TokenStoreFactory.create(applicationContext).read()?.accessToken
    if(access.isNullOrBlank()) return if(action=="register") Result.success() else Result.retry()
    if(action=="register" && fcm.isNullOrBlank()) return Result.success()
    val packageInfo=applicationContext.packageManager.getPackageInfo(applicationContext.packageName,0)
    val request=PushInstallationRequest(InstallationIdentity(applicationContext).id(),if(action=="register") fcm else null,
      packageInfo.versionName ?: "unknown",if(Build.VERSION.SDK_INT>=28) packageInfo.longVersionCode.toInt() else @Suppress("DEPRECATION") packageInfo.versionCode,
      locale=Locale.getDefault().toLanguageTag(),timezone=TimeZone.getDefault().id,permissionStatus=prefs.getString("permission","NotRequested")!!)
    return try {
      val api=MasaryNetwork.studentPushTokenApi(baseUrl); val response=if(action=="register") api.register("Bearer $access",request) else api.unregister("Bearer $access",request)
      if(response.success) { if(action=="register") SecurePendingTokenStore(applicationContext).clear() else logoutStore.clear(); Result.success() } else Result.retry()
    } catch(_:Exception) { Result.retry() }
 }
}
