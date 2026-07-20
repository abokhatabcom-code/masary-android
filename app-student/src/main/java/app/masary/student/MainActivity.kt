package app.masary.student

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.datastore.preferences.preferencesDataStore
import app.masary.core.datastore.DataStoreSessionManager
import app.masary.core.security.TokenStoreFactory
import app.masary.core.ui.MasaryTheme
import app.masary.feature.auth.data.AuthRepositoryFactory
import app.masary.feature.auth.ui.AuthRoute
import app.masary.feature.home.data.HomeRepositoryFactory
import app.masary.feature.home.ui.StudentHomeRoute

private val ComponentActivity.sessionDataStore by preferencesDataStore(name = "student_session")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val tokenStore = TokenStoreFactory.create(applicationContext)
        val sessionManager = DataStoreSessionManager(sessionDataStore, tokenStore)
        val homeRepository = HomeRepositoryFactory.create(sessionManager)
        setContent {
            MasaryTheme {
                AuthRoute(
                    repository = AuthRepositoryFactory.create(),
                    sessionManager = sessionManager,
                    deviceName = Build.MODEL.ifBlank { "Android" },
                    authenticatedContent = { session, onLogout ->
                        StudentHomeRoute(
                            session = session,
                            repository = homeRepository,
                            onLogout = onLogout,
                        )
                    },
                )
            }
        }
    }
}
