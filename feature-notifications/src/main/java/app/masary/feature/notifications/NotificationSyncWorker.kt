package app.masary.feature.notifications
import android.content.Context
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.masary.core.network.MasaryNetwork
import app.masary.core.network.auth.StudentLogoutRequestDto
import app.masary.core.network.auth.StudentRefreshRequestDto
import app.masary.core.network.notifications.PushInstallationRequest
import app.masary.core.security.TokenStoreFactory
import com.google.gson.Gson
import java.io.IOException
import java.util.Locale
import java.util.TimeZone
import retrofit2.HttpException

class NotificationSyncWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
 override suspend fun doWork():Result {
  val prefs=applicationContext.getSharedPreferences("notification_runtime_v1",Context.MODE_PRIVATE)
  val action=inputData.getString("action")?:return Result.failure();val base=prefs.getString("base_url",null)?:return Result.failure()
  val logoutStore=SecurePendingTokenStore(applicationContext,"logout_session")
  var pending=logoutStore.read()?.let{runCatching{Gson().fromJson(it,PendingLogout::class.java)}.getOrNull()}
  var access=if(action=="unregister")pending?.accessToken else TokenStoreFactory.create(applicationContext).read()?.accessToken
  if(access.isNullOrBlank())return if(action=="register")Result.success()else permanent(logoutStore)
  val fcm=SecurePendingTokenStore(applicationContext).read();val info=applicationContext.packageManager.getPackageInfo(applicationContext.packageName,0)
  val request=PushInstallationRequest(InstallationIdentity(applicationContext).id(),if(action=="register")fcm else null,info.versionName?:"unknown",if(Build.VERSION.SDK_INT>=28)info.longVersionCode.toInt()else @Suppress("DEPRECATION") info.versionCode,locale=Locale.getDefault().toLanguageTag(),timezone=TimeZone.getDefault().id,permissionStatus=prefs.getString("permission","NotRequested")!!)
  return try {
   val push=MasaryNetwork.studentPushTokenApi(base)
   if(action=="unregister") {
    var unregistered=try{push.unregister("Bearer $access",request)}catch(e:HttpException){
     if(e.code()!=401||pending==null)throw e
     val refresh=MasaryNetwork.studentAuthApi(base).refresh(StudentRefreshRequestDto(pending.refreshToken))
     val refreshed=refresh.data?.takeIf{refresh.success}?:return permanent(logoutStore)
     pending=pending.rotated(refreshed.accessToken,refreshed.refreshToken)
     logoutStore.write(Gson().toJson(pending));access=refreshed.accessToken
     push.unregister("Bearer $access",request)
    }
    if(!unregistered.success)return permanent(logoutStore)
    val active=requireNotNull(pending)
    val loggedOut=MasaryNetwork.studentAuthApi(base).logout("Bearer $access",StudentLogoutRequestDto(active.refreshToken))
    if(!loggedOut.success)return permanent(logoutStore)
    logoutStore.clear()
   }else{
    val registered=push.register("Bearer $access",request);if(!registered.success)return Result.failure()
    if(fcm!=null)SecurePendingTokenStore(applicationContext).clear()
   }
   Result.success()
  }catch(e:HttpException){decision(e.code(),null,logoutStore,action)}
   catch(e:Throwable){decision(null,e,logoutStore,action)}
 }
 private fun decision(code:Int?,error:Throwable?,store:SecurePendingTokenStore,action:String):Result =
  if(SyncFailurePolicy.classify(code,error,runAttemptCount)==SyncDecision.Retry)Result.retry()else{if(action=="unregister")store.clear();Result.failure()}
 private fun permanent(store:SecurePendingTokenStore):Result{store.clear();return Result.failure()}
}
