package app.masary.student

import android.os.Build
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.mutableStateOf
import android.Manifest
import androidx.activity.result.contract.ActivityResultContracts
import app.masary.feature.notifications.*
import androidx.activity.ComponentActivity
import androidx.core.app.NotificationManagerCompat
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
    private val notificationDestination = mutableStateOf<String?>(null)
    private val permissionState = mutableStateOf(NotificationPermissionState.NotRequested)
    private val permissionController = NotificationPermissionController()
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        getSharedPreferences("notification_permission_v1", MODE_PRIVATE).edit().putBoolean("requested", true).apply()
        refreshPermissionState(granted)
        NotificationSyncCoordinator.updatePermission(this, permissionState.value)
        NotificationSyncCoordinator.scheduleRegistration(this)
    }
    private fun refreshPermissionState(granted: Boolean = NotificationManagerCompat.from(this).areNotificationsEnabled()) {
        val requested = getSharedPreferences("notification_permission_v1", MODE_PRIVATE).getBoolean("requested", false)
        permissionState.value = permissionController.state(Build.VERSION.SDK_INT, granted, requested, shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))
    }
    override fun onResume() {
        super.onResume()
        refreshPermissionState()
        NotificationSyncCoordinator.updatePermission(this, permissionState.value)
        NotificationSyncCoordinator.scheduleRegistration(this)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notificationDestination.value = intent?.getStringExtra("notification_destination")
        enableEdgeToEdge()
        NotificationChannels.create(this)
        refreshPermissionState()
        NotificationSyncCoordinator.updatePermission(this, permissionState.value)
        FirebaseInitializer.initialize(this, BuildConfig.FIREBASE_PROJECT_ID, BuildConfig.FIREBASE_APPLICATION_ID, BuildConfig.FIREBASE_API_KEY, BuildConfig.FIREBASE_GCM_SENDER_ID)
        NotificationSyncCoordinator.configure(this, BuildConfig.MASARY_API_BASE_URL, false)
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
                    notificationPermissionState = permissionState.value,
                    notificationDestination = notificationDestination.value,
                    onNotificationDestinationConsumed = { notificationDestination.value = null },
                    onNotificationsPermission = { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) },
                    onOpenNotificationSettings = { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) },
                )
            }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        notificationDestination.value = intent.getStringExtra("notification_destination")
    }

}
