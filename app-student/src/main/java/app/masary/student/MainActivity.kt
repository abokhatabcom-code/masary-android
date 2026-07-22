package app.masary.student

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.datastore.preferences.preferencesDataStore
import app.masary.core.datastore.DataStoreSessionManager
import app.masary.core.datastore.DataStoreOnboardingStore
import app.masary.core.security.TokenStoreFactory
import app.masary.core.ui.MasaryTheme
import app.masary.feature.auth.data.AuthRepositoryFactory
import app.masary.feature.home.data.HomeRepositoryFactory

private val ComponentActivity.sessionDataStore by preferencesDataStore(name = "student_session")
private val ComponentActivity.onboardingDataStore by preferencesDataStore(name = "student_onboarding")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val tokenStore = TokenStoreFactory.create(applicationContext)
        val sessionManager = DataStoreSessionManager(sessionDataStore, tokenStore)
        val onboardingStore = DataStoreOnboardingStore(onboardingDataStore)
        val authRepository = AuthRepositoryFactory.create(BuildConfig.MASARY_API_BASE_URL)
        val registrationRepository = AuthRepositoryFactory.createRegistration(BuildConfig.MASARY_API_BASE_URL)
        val homeRepository = HomeRepositoryFactory.create(sessionManager, BuildConfig.MASARY_API_BASE_URL)
        setContent {
            MasaryTheme {
                MasaryStudentApp(
                    onboardingStore = onboardingStore,
                    sessionManager = sessionManager,
                    authRepository = authRepository,
                    registrationRepository = registrationRepository,
                    homeRepository = homeRepository,
                    deviceName = Build.MODEL.ifBlank { "Android" },
                )
            }
        }
    }
}
