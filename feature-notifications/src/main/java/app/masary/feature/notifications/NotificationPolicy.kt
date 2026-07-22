package app.masary.feature.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** Android Notifications V1 policy. Permission is requested only from visible UI after an explanation. */
object NotificationPolicy {
    const val CHANNEL_LEARNING = "masary_learning_v1"
    fun requiresRuntimePermission(sdk: Int = Build.VERSION.SDK_INT): Boolean = sdk >= 33
    fun isGranted(context: Context): Boolean = !requiresRuntimePermission() ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL_LEARNING, context.getString(R.string.notification_channel_learning), NotificationManager.IMPORTANCE_DEFAULT))
        }
    }
}
