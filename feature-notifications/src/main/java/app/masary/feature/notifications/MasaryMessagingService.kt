package app.masary.feature.notifications

import com.google.firebase.messaging.FirebaseMessagingService

/** Receives token rotation locally. Upload is deliberately delegated to authenticated app code. */
class MasaryMessagingService : FirebaseMessagingService() {
    override fun onCreate() { super.onCreate(); NotificationPolicy.createChannels(this) }
    override fun onNewToken(token: String) {
        getSharedPreferences("android_notifications_v1", MODE_PRIVATE).edit().putString("pending_fcm_token", token).apply()
    }
}
