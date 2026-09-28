package app.masary.student

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.NotificationManagerCompat
import androidx.datastore.preferences.preferencesDataStore
import app.masary.core.datastore.DataStoreOnboardingStore
import app.masary.core.datastore.DataStoreSessionManager
import app.masary.core.local.MasaryLocalDatabase
import app.masary.core.local.RoomStudentLocalStore
import app.masary.core.security.TokenStoreFactory
import app.masary.core.ui.MasaryTheme
import app.masary.feature.activitypreparation.ActivityPreparationRepositoryFactory
import app.masary.feature.auth.data.AuthRepositoryFactory
import app.masary.feature.home.data.HomeRepositoryFactory
import app.masary.feature.notifications.*
import app.masary.feature.questionsession.data.QuestionSessionRepositoryFactory
import app.masary.feature.subject.data.SubjectRepositoryFactory
import app.masary.feature.subjects.data.SubjectsRepositoryFactory
import app.masary.feature.trainingcenter.data.TrainingCenterRepositoryFactory

private val ComponentActivity.sessionDataStore by preferencesDataStore(name = "student_session")
private val ComponentActivity.onboardingDataStore by preferencesDataStore(name = "student_onboarding")
private val ComponentActivity.homeSnapshotDataStore by preferencesDataStore(name = "student_home_snapshot")
private val ComponentActivity.subjectsSnapshotDataStore by preferencesDataStore(name = "student_subjects_snapshot")
private val ComponentActivity.subjectSnapshotDataStore by preferencesDataStore(name = "student_subject_page_snapshots")
private val ComponentActivity.trainingCenterSnapshotDataStore by preferencesDataStore(
    name = "student_training_center_snapshots",
)
private val ComponentActivity.activityPreparationDataStore by preferencesDataStore(name = "student_activity_preparation")

class MainActivity : ComponentActivity() {
    private val destinationInbox = NotificationDestinationInbox()
    private val notificationDestination = mutableStateOf<String?>(null)
    private val permissionState = mutableStateOf(NotificationPermissionState.NotRequested)
    private val permissionController = NotificationPermissionController()
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        getSharedPreferences("notification_permission_v1", MODE_PRIVATE).edit().putBoolean("requested", true).apply()
        refreshPermissionState(granted)
        NotificationSyncCoordinator.updatePermission(this, permissionState.value)
        NotificationSyncCoordinator.scheduleRegistration(this)
    }

    private fun refreshPermissionState(
        granted: Boolean = NotificationManagerCompat.from(this).areNotificationsEnabled(),
    ) {
        val requested = getSharedPreferences("notification_permission_v1", MODE_PRIVATE)
            .getBoolean("requested", false)
        permissionState.value = permissionController.state(
            Build.VERSION.SDK_INT,
            granted,
            requested,
            shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS),
        )
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionState()
        NotificationSyncCoordinator.updatePermission(this, permissionState.value)
        NotificationSyncCoordinator.scheduleRegistration(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        destinationInbox.receive(intent?.getStringExtra("notification_destination"))
        notificationDestination.value = destinationInbox.consume(true, false) {
            intent?.removeExtra("notification_destination")
        }
        enableEdgeToEdge()
        NotificationChannels.create(this)
        refreshPermissionState()
        NotificationSyncCoordinator.updatePermission(this, permissionState.value)
        FirebaseInitializer.initialize(
            this,
            BuildConfig.FIREBASE_PROJECT_ID,
            BuildConfig.FIREBASE_APPLICATION_ID,
            BuildConfig.FIREBASE_API_KEY,
            BuildConfig.FIREBASE_GCM_SENDER_ID,
        )
        NotificationSyncCoordinator.configure(this, BuildConfig.MASARY_API_BASE_URL, false)

        val tokenStore = TokenStoreFactory.create(applicationContext)
        val sessionManager = DataStoreSessionManager(sessionDataStore, tokenStore)
        val onboardingStore = DataStoreOnboardingStore(onboardingDataStore)
        val localStore = RoomStudentLocalStore(MasaryLocalDatabase.get(applicationContext))
        val authRepository = AuthRepositoryFactory.create(BuildConfig.MASARY_API_BASE_URL)
        val registrationRepository = AuthRepositoryFactory.createRegistration(BuildConfig.MASARY_API_BASE_URL)
        val homeRepository = HomeRepositoryFactory.create(
            sessionManager,
            BuildConfig.MASARY_API_BASE_URL,
            homeSnapshotDataStore,
            localStore,
        )
        val subjectsRepository = SubjectsRepositoryFactory.create(
            sessionManager,
            BuildConfig.MASARY_API_BASE_URL,
            subjectsSnapshotDataStore,
            localStore,
        )
        val subjectRepository = SubjectRepositoryFactory.create(
            sessionManager,
            BuildConfig.MASARY_API_BASE_URL,
            subjectSnapshotDataStore,
            localStore,
        )
        val trainingCenterRepository = TrainingCenterRepositoryFactory.create(
            sessionManager,
            BuildConfig.MASARY_API_BASE_URL,
            trainingCenterSnapshotDataStore,
        )
        val activityPreparation = ActivityPreparationRepositoryFactory.create(
            sessionManager,
            BuildConfig.MASARY_API_BASE_URL,
            activityPreparationDataStore,
        )
        val questionSessionRepository = QuestionSessionRepositoryFactory.create(
            context = applicationContext,
            sessionManager = sessionManager,
            localStore = localStore,
            baseUrl = BuildConfig.MASARY_API_BASE_URL,
        )

        setContent {
            MasaryTheme {
                MasaryStudentApp(
                    onboardingStore = onboardingStore,
                    sessionManager = sessionManager,
                    localStore = localStore,
                    authRepository = authRepository,
                    registrationRepository = registrationRepository,
                    homeRepository = homeRepository,
                    subjectsRepository = subjectsRepository,
                    subjectRepository = subjectRepository,
                    trainingCenterRepository = trainingCenterRepository,
                    questionSessionRepository = questionSessionRepository,
                    activityPreparationRepository = activityPreparation.repository,
                    activityPreparationPendingStore = activityPreparation.pendingStore,
                    deviceName = Build.MODEL.ifBlank { "Android" },
                    notificationPermissionState = permissionState.value,
                    notificationDestination = notificationDestination.value,
                    onNotificationDestinationConsumed = {
                        notificationDestination.value = null
                        intent?.removeExtra("notification_destination")
                    },
                    onNotificationsPermission = {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    },
                    onOpenNotificationSettings = {
                        startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:$packageName"),
                            ),
                        )
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        destinationInbox.receive(intent.getStringExtra("notification_destination"))
        notificationDestination.value = destinationInbox.consume(true, false) {
            intent.removeExtra("notification_destination")
        }
    }
}
