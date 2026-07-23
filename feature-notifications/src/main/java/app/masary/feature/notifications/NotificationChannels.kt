package app.masary.feature.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationChannels {
    const val ACCOUNT_SECURITY = "account_security"
    const val LEARNING_REMINDERS = "learning_reminders"
    const val NEWS_OFFERS = "news_offers"
    const val SYSTEM_UPDATES = "system_updates"
    val ids = setOf(ACCOUNT_SECURITY, LEARNING_REMINDERS, NEWS_OFFERS, SYSTEM_UPDATES)

    fun create(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(NotificationManager::class.java)
        listOf(
            Triple(ACCOUNT_SECURITY, R.string.channel_security_name, R.string.channel_security_description),
            Triple(LEARNING_REMINDERS, R.string.channel_learning_name, R.string.channel_learning_description),
            Triple(NEWS_OFFERS, R.string.channel_news_name, R.string.channel_news_description),
            Triple(SYSTEM_UPDATES, R.string.channel_system_name, R.string.channel_system_description),
        ).forEach { (id, name, description) ->
            // createNotificationChannel is idempotent and preserves user choices for existing channels.
            manager.createNotificationChannel(NotificationChannel(id, context.getString(name), NotificationManager.IMPORTANCE_DEFAULT).apply {
                this.description = context.getString(description)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            })
        }
    }
}
