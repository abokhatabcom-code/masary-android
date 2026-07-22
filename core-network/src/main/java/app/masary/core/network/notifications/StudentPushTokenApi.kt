package app.masary.core.network.notifications
import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.Header
import retrofit2.http.PUT

data class PushInstallationRequest(
 @SerializedName("installation_id") val installationId:String,
 @SerializedName("fcm_token") val fcmToken:String?=null,
 @SerializedName("app_version") val appVersion:String,
 @SerializedName("app_build") val appBuild:Int,
 val platform:String="android", val locale:String, val timezone:String,
 @SerializedName("permission_status") val permissionStatus:String,
) { override fun toString() = "PushInstallationRequest(installationId=$installationId, fcmToken=[REDACTED], appVersion=$appVersion, appBuild=$appBuild, platform=$platform, locale=$locale, timezone=$timezone, permissionStatus=$permissionStatus)" }
data class PushTokenData(val registered:Boolean)
data class PushTokenResponse(val success:Boolean,val data:PushTokenData?,val error:PushTokenError?)
data class PushTokenError(val code:String,val message:String)
interface StudentPushTokenApi {
 @PUT("api/v1/student/push-token") suspend fun register(@Header("Authorization") authorization:String,@Body request:PushInstallationRequest):PushTokenResponse
 @DELETE("api/v1/student/push-token") suspend fun unregister(@Header("Authorization") authorization:String,@Body request:PushInstallationRequest):PushTokenResponse
}
