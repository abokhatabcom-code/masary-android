package app.masary.feature.notifications

import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MasaryMessagingService : FirebaseMessagingService() {
    override fun onCreate() { super.onCreate(); NotificationChannels.create(this) }
    override fun onNewToken(token: String) {
        SecurePendingTokenStore(this).write(token)
        NotificationSyncCoordinator.scheduleRegistration(this)
    }
    override fun onMessageReceived(message: RemoteMessage) {
        val state = getSharedPreferences("notification_runtime_v1", MODE_PRIVATE)
        val destination = NotificationDestinationPolicy.resolve(
            message.data["destination"], state.getBoolean("has_session", false), state.getBoolean("educational_test_active", false),
        )
        val channel = message.data["channel"]?.takeIf(NotificationChannels.ids::contains) ?: NotificationChannels.SYSTEM_UPDATES
        val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("notification_destination", destination)
        } ?: return
        val fingerprint = message.data.toSortedMap().entries.joinToString("|") { "${it.key}=${it.value}" }
        val requestCode = NotificationPendingIntentPolicy.requestCode(message.messageId, message.sentTime, destination, fingerprint)
        val pending = PendingIntent.getActivity(this, requestCode, intent, PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, channel)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setContentTitle(message.data["title"]?.take(80) ?: getString(R.string.channel_system_name))
            .setContentText(message.data["body"]?.take(160).orEmpty())
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setContentIntent(pending).setAutoCancel(true).build()
        if (NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            NotificationManagerCompat.from(this).notify(requestCode, notification)
        }
    }
}
