package app.masary.student

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.masary.core.datastore.OnboardingStore
import app.masary.core.datastore.SessionManager
import app.masary.core.local.StudentLocalStore
import app.masary.core.ui.MasaryBrandLockup
import app.masary.core.ui.MasaryColors
import app.masary.feature.activitypreparation.ActivityPreparationPendingStore
import app.masary.feature.activitypreparation.ActivityPreparationRepository
import app.masary.feature.auth.domain.AuthRepository
import app.masary.feature.auth.domain.RegistrationRepository
import app.masary.feature.auth.ui.AuthRoute
import app.masary.feature.home.domain.HomeRepository
import app.masary.feature.home.ui.StudentHomeRoute
import app.masary.feature.notifications.NotificationDestinationPolicy
import app.masary.feature.notifications.NotificationPermissionState
import app.masary.feature.notifications.NotificationSyncCoordinator
import app.masary.feature.questionsession.domain.QuestionSessionRepository
import app.masary.feature.subject.domain.SubjectRepository
import app.masary.feature.subjects.domain.SubjectsRepository
import app.masary.feature.trainingcenter.domain.TrainingCenterRepository
import kotlinx.coroutines.launch

private enum class AppRoute(val route: String) {
    Preparing("preparing"),
    Onboarding("onboarding"),
    Authentication("authentication"),
    Home("home"),
    RecoverableError("recoverable-error"),
}

@Composable
fun MasaryStudentApp(
    onboardingStore: OnboardingStore,
    sessionManager: SessionManager,
    localStore: StudentLocalStore,
    authRepository: AuthRepository,
    registrationRepository: RegistrationRepository,
    homeRepository: HomeRepository,
    subjectsRepository: SubjectsRepository,
    subjectRepository: SubjectRepository,
    trainingCenterRepository: TrainingCenterRepository,
    questionSessionRepository: QuestionSessionRepository,
    activityPreparationRepository: ActivityPreparationRepository,
    activityPreparationPendingStore: ActivityPreparationPendingStore,
    deviceName: String,
    notificationPermissionState: NotificationPermissionState = NotificationPermissionState.NotRequired,
    notificationDestination: String? = null,
    onNotificationDestinationConsumed: () -> Unit = {},
    onNotificationsPermission: () -> Unit = {},
    onOpenNotificationSettings: () -> Unit = {},
) {
    val startupViewModel: StartupViewModel = viewModel(
        factory = StartupViewModelFactory(onboardingStore, sessionManager, authRepository),
    )
    val state by startupViewModel.state.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val route = state.route()
    val scope = rememberCoroutineScope()

    LaunchedEffect(route) {
        if (route == AppRoute.Authentication || route == AppRoute.Onboarding) {
            onNotificationDestinationConsumed()
        }
        if (route == AppRoute.Home) {
            NotificationSyncCoordinator.configure(
                navController.context,
                BuildConfig.MASARY_API_BASE_URL,
                true,
            )
            NotificationSyncCoordinator.scheduleRegistration(navController.context)
        }
        if (navController.currentDestination?.route != route.route) {
            navController.navigate(route.route) {
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    NavHost(navController = navController, startDestination = AppRoute.Preparing.route) {
        composable(AppRoute.Preparing.route) { PreparingScreen() }
        composable(AppRoute.Onboarding.route) {
            OnboardingScreen(onComplete = startupViewModel::completeOnboarding)
        }
        composable(AppRoute.Authentication.route) {
            AuthRoute(
                repository = authRepository,
                registrationRepository = registrationRepository,
                sessionManager = sessionManager,
                deviceName = deviceName,
                onAuthenticated = startupViewModel::authenticated,
            )
        }
        composable(AppRoute.Home.route) {
            val authenticated = state as? StartupState.Authenticated
            if (authenticated == null) {
                PreparingScreen()
            } else {
                StudentHomeRoute(
                    session = authenticated.session,
                    repository = homeRepository,
                    subjectsRepository = subjectsRepository,
                    subjectRepository = subjectRepository,
                    trainingCenterRepository = trainingCenterRepository,
                    questionSessionRepository = questionSessionRepository,
                    activityPreparationRepository = activityPreparationRepository,
                    activityPreparationPendingStore = activityPreparationPendingStore,
                    onLogout = {
                        scope.launch {
                            val tokens = sessionManager.readTokens()
                            val prepared = tokens == null || NotificationSyncCoordinator.scheduleUnregister(
                                navController.context,
                                tokens.accessToken,
                                tokens.refreshToken,
                            )
                            if (prepared) {
                                try {
                                    localStore.clearStudent(authenticated.session.id)
                                    homeRepository.clearSnapshot()
                                    subjectsRepository.clearSnapshot()
                                    subjectRepository.clearSnapshots()
                                    trainingCenterRepository.clearSnapshots()
                                    activityPreparationPendingStore.clear()
                                } catch (_: Exception) {
                                    // Local logout must not be blocked by a damaged cache store.
                                }
                                startupViewModel.logoutLocally()
                            } else {
                                startupViewModel.logoutPreparationFailed()
                            }
                        }
                    },
                    externalDestination = notificationDestination?.let {
                        val testActive = navController.context
                            .getSharedPreferences("notification_runtime_v1", 0)
                            .getBoolean("educational_test_active", false)
                        NotificationDestinationPolicy.resolve(it, true, testActive)
                    },
                    onExternalDestinationConsumed = onNotificationDestinationConsumed,
                    permissionState = notificationPermissionState,
                    onNotificationsPermission = onNotificationsPermission,
                    onOpenNotificationSettings = onOpenNotificationSettings,
                )
            }
        }
        composable(AppRoute.RecoverableError.route) {
            RecoverableErrorScreen(onRetry = startupViewModel::retry)
        }
    }
}

private fun StartupState.route(): AppRoute = when (this) {
    StartupState.Preparing -> AppRoute.Preparing
    StartupState.NeedsOnboarding -> AppRoute.Onboarding
    StartupState.NeedsAuthentication -> AppRoute.Authentication
    is StartupState.Authenticated -> AppRoute.Home
    StartupState.RecoverableError -> AppRoute.RecoverableError
}

@Composable
private fun PreparingScreen() {
    Surface(color = MasaryColors.background, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            MasaryBrandLockup()
            Spacer(Modifier.height(28.dp))
            CircularProgressIndicator(color = MasaryColors.brandGold)
            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.preparing_app), color = MasaryColors.muted)
        }
    }
}

private data class OnboardingPage(val title: Int, val body: Int)

@Composable
private fun OnboardingScreen(onComplete: () -> Unit) {
    val pages = remember {
        listOf(
            OnboardingPage(R.string.onboarding_learn_title, R.string.onboarding_learn_body),
            OnboardingPage(R.string.onboarding_progress_title, R.string.onboarding_progress_body),
            OnboardingPage(R.string.onboarding_ready_title, R.string.onboarding_ready_body),
        )
    }
    var page by rememberSaveable { mutableIntStateOf(0) }
    val lastPage = page == pages.lastIndex

    Surface(color = MasaryColors.background, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 20.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onComplete) { Text(stringResource(R.string.skip)) }
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                MasaryBrandLockup()
                Spacer(Modifier.height(44.dp))
                Text(
                    text = stringResource(pages[page].title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MasaryColors.brandNavy,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(pages[page].body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MasaryColors.muted,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(30.dp))
                Text(
                    text = stringResource(R.string.onboarding_page, page + 1, pages.size),
                    color = MasaryColors.brandGold,
                    fontWeight = FontWeight.Bold,
                )
            }
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    if (lastPage) onComplete() else page += 1
                },
            ) {
                Text(stringResource(if (lastPage) R.string.start_now else R.string.continue_label))
            }
        }
    }
}

@Composable
private fun RecoverableErrorScreen(onRetry: () -> Unit) {
    Surface(color = MasaryColors.background, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.startup_error_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.startup_error_body),
                color = MasaryColors.muted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        }
    }
}
