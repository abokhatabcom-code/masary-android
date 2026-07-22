package app.masary.feature.notifications
import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging

object FirebaseInitializer {
    fun configurationComplete(projectId: String, applicationId: String, apiKey: String, senderId: String): Boolean =
        listOf(projectId, applicationId, apiKey, senderId).none(String::isBlank)
    /** Missing configuration explicitly disables FCM without affecting the rest of the app. */
    fun initialize(context: Context, projectId: String, applicationId: String, apiKey: String, senderId: String): Boolean {
        if (!configurationComplete(projectId, applicationId, apiKey, senderId)) return false
        if (FirebaseApp.getApps(context).isEmpty()) FirebaseApp.initializeApp(context, FirebaseOptions.Builder().setProjectId(projectId).setApplicationId(applicationId).setApiKey(apiKey).setGcmSenderId(senderId).build())
        FirebaseMessaging.getInstance().isAutoInitEnabled = true
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            if (token.isNotBlank()) { SecurePendingTokenStore(context).write(token); NotificationSyncCoordinator.scheduleRegistration(context) }
        }
        return true
    }
}
