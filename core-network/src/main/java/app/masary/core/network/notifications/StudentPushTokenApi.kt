package app.masary.core.network.notifications
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.Header
import retrofit2.http.PUT

data class PushTokenRequest(val token: String, val platform: String = "android", val app: String = "student")
data class PushTokenData(val registered: Boolean)
data class PushTokenResponse(val success: Boolean, val data: PushTokenData?, val error: PushTokenError?)
data class PushTokenError(val code: String, val message: String)
interface StudentPushTokenApi {
 @PUT("api/v1/student/push-token") suspend fun register(@Header("Authorization") authorization: String,@Body request: PushTokenRequest): PushTokenResponse
 @DELETE("api/v1/student/push-token") suspend fun unregister(@Header("Authorization") authorization: String,@Body request: PushTokenRequest): PushTokenResponse
}
