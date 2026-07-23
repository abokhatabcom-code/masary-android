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
import kotlinx.coroutines.CancellationException
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
    val initial=requireNotNull(pending)
    val auth=MasaryNetwork.studentAuthApi(base)
    val transport=object:LogoutTransport {
     override suspend fun unregister(accessToken:String)=outcome { push.unregister("Bearer $accessToken",request).success }
     override suspend fun refresh(refreshToken:String):RefreshOutcome = try { val response=auth.refresh(StudentRefreshRequestDto(refreshToken));val data=response.data;if(response.success&&data!=null)RefreshOutcome.Success(RefreshedCredentials(data.accessToken,data.refreshToken))else RefreshOutcome.PermanentFailure } catch(error:CancellationException){throw error}catch(error:HttpException){if(SyncFailurePolicy.classify(error.code(),attempt=runAttemptCount)==SyncDecision.Retry)RefreshOutcome.TemporaryFailure else RefreshOutcome.PermanentFailure}catch(error:java.io.IOException){RefreshOutcome.TemporaryFailure}catch(error:Exception){RefreshOutcome.PermanentFailure}
     override suspend fun logout(accessToken:String,refreshToken:String)=outcome { auth.logout("Bearer $accessToken",StudentLogoutRequestDto(refreshToken)).success }
     private suspend fun outcome(call:suspend()->Boolean):TransportOutcome = try { if(call())TransportOutcome.Success else TransportOutcome.PermanentFailure } catch(error:CancellationException){throw error}catch(error:HttpException){when{error.code()==401->TransportOutcome.Unauthorized;SyncFailurePolicy.classify(error.code(),attempt=runAttemptCount)==SyncDecision.Retry->TransportOutcome.TemporaryFailure;else->TransportOutcome.PermanentFailure}}catch(error:java.io.IOException){TransportOutcome.TemporaryFailure}catch(error:Exception){TransportOutcome.PermanentFailure}
    }
    when(LogoutSequence(transport){logoutStore.write(Gson().toJson(it))}.run(initial)) {
     LogoutSequenceResult.Success->{logoutStore.clear()}
     LogoutSequenceResult.Retry->return if(runAttemptCount<SyncFailurePolicy.MAX_ATTEMPTS-1)Result.retry()else permanent(logoutStore)
     LogoutSequenceResult.PermanentFailure->return permanent(logoutStore)
    }
   }else{
    val registered=push.register("Bearer $access",request);if(!registered.success)return Result.failure()
    if(SecurePendingTokenStore(applicationContext).read()!=fcm)return Result.retry()
    // Retain the current token encrypted so the same installation can bind a later account session.
   }
   Result.success()
  }catch(error:CancellationException){throw error}
   catch(e:HttpException){decision(e.code(),null,logoutStore,action)}
   catch(e:java.io.IOException){decision(null,e,logoutStore,action)}
   catch(e:Exception){decision(null,e,logoutStore,action)}
 }
 private fun decision(code:Int?,error:Throwable?,store:SecurePendingTokenStore,action:String):Result =
  if(SyncFailurePolicy.classify(code,error,runAttemptCount)==SyncDecision.Retry)Result.retry()else{if(action=="unregister")store.clear();Result.failure()}
 private fun permanent(store:SecurePendingTokenStore):Result{store.clear();return Result.failure()}
}
