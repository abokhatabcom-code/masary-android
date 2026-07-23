package app.masary.feature.notifications

import android.content.Context
import java.util.UUID

class InstallationIdentity(context: Context) {
    private val preferences = context.getSharedPreferences("notification_installation_v1", Context.MODE_PRIVATE)
    fun id(): String = preferences.getString(KEY, null) ?: UUID.randomUUID().toString().also {
        preferences.edit().putString(KEY, it).apply()
    }
    private companion object { const val KEY = "random_installation_uuid" }
}
