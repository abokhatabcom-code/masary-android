package app.masary.student

import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.datastore.preferences.preferencesDataStore
import app.masary.core.datastore.DataStoreSessionManager
import app.masary.core.ui.MasaryTheme
import app.masary.core.security.TokenStoreFactory
import app.masary.feature.auth.data.AuthRepositoryFactory
import app.masary.feature.auth.ui.AuthRoute

private val ComponentActivity.sessionDataStore by preferencesDataStore(name = "student_session")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val sessionManager = DataStoreSessionManager(sessionDataStore, TokenStoreFactory.create())
        setContent {
            MasaryTheme {
                AuthRoute(
                    repository = AuthRepositoryFactory.create(),
                    sessionManager = sessionManager,
                    deviceName = Build.MODEL.ifBlank { "Android" },
                )
            }
        }
    }
}
