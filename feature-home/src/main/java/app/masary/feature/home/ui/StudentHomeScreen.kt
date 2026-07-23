package app.masary.feature.home.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Diamond
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Badge
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable
import app.masary.core.models.auth.StudentSession
import app.masary.core.ui.MasaryBrandLockup
import app.masary.core.ui.MasaryBrandMark
import app.masary.core.ui.MasaryColors
import app.masary.core.ui.MasaryOrbitDecoration
import app.masary.feature.home.R
import app.masary.feature.home.domain.HomeRepository
import app.masary.feature.home.domain.HomeSmartGuide
import app.masary.feature.home.domain.HomeSmartGuideStep
import app.masary.feature.home.domain.StudentHomeData
import app.masary.feature.home.domain.HomeSpotlight
import app.masary.feature.notifications.NotificationPermissionState

@Serializable internal sealed interface StudentDestination {
    @Serializable data object Home : StudentDestination
    @Serializable data object Subjects : StudentDestination
    @Serializable data object Guide : StudentDestination
    @Serializable data object Ranking : StudentDestination
    @Serializable data object Profile : StudentDestination
    @Serializable data class SubjectDetails(val subjectVersionId: Int) : StudentDestination
}

internal val studentDestinations = listOf(StudentDestination.Home, StudentDestination.Subjects, StudentDestination.Guide, StudentDestination.Ranking, StudentDestination.Profile)
private val StudentDestination.labelRes: Int get() = when (this) {
    StudentDestination.Home -> R.string.nav_home; StudentDestination.Subjects -> R.string.nav_subjects
    StudentDestination.Guide -> R.string.nav_guide; StudentDestination.Ranking -> R.string.nav_ranking
    StudentDestination.Profile -> R.string.nav_profile
    is StudentDestination.SubjectDetails -> R.string.nav_subjects
}
private val StudentDestination.icon: ImageVector get() = when (this) {
    StudentDestination.Home -> Icons.Outlined.Home; StudentDestination.Subjects -> Icons.AutoMirrored.Outlined.MenuBook
    StudentDestination.Guide -> Icons.Outlined.AutoAwesome; StudentDestination.Ranking -> Icons.Outlined.EmojiEvents
    StudentDestination.Profile -> Icons.Outlined.Person
    is StudentDestination.SubjectDetails -> Icons.AutoMirrored.Outlined.MenuBook
}

@Composable
fun StudentHomeRoute(
    session: StudentSession,
    repository: HomeRepository,
    onLogout: () -> Unit,
    externalDestination: String? = null,
    onExternalDestinationConsumed: () -> Unit = {},
    permissionState: NotificationPermissionState = NotificationPermissionState.NotRequired,
    onNotificationsPermission: () -> Unit = {},
    onOpenNotificationSettings: () -> Unit = {},
) {
    val homeViewModel: StudentHomeViewModel = viewModel(
        factory = StudentHomeViewModelFactory(repository),
    )
    val state by homeViewModel.state.collectAsStateWithLifecycle()
    val studentNavController = rememberNavController()
    val backStack by studentNavController.currentBackStackEntryAsState()
    val destination = when {
        backStack?.destination?.hasRoute<StudentDestination.Subjects>() == true ||
            backStack?.destination?.hasRoute<StudentDestination.SubjectDetails>() == true -> StudentDestination.Subjects
        backStack?.destination?.hasRoute<StudentDestination.Guide>() == true -> StudentDestination.Guide
        backStack?.destination?.hasRoute<StudentDestination.Ranking>() == true -> StudentDestination.Ranking
        backStack?.destination?.hasRoute<StudentDestination.Profile>() == true -> StudentDestination.Profile
        else -> StudentDestination.Home
    }
    val snackbarHostState = remember { SnackbarHostState() }
    var showPermissionExplanation by rememberSaveable { mutableStateOf(permissionState == NotificationPermissionState.NotRequested) }
    LaunchedEffect(externalDestination) {
        externalDestination?.let { requested ->
            val target = studentDestinations.firstOrNull { it::class.simpleName.equals(requested, ignoreCase = true) } ?: StudentDestination.Home
            studentNavController.navigate(target) { launchSingleTop = true; popUpTo(StudentDestination.Home) { saveState = true }; restoreState = true }
            onExternalDestinationConsumed()
        }
    }

    if (showPermissionExplanation) {
        AlertDialog(
            onDismissRequest = { showPermissionExplanation = false },
            title = { Text("ابقَ على اطلاع") },
            text = { Text("اسمح لمساري بإرسال تذكيرات التعلّم وتنبيهات أمان الحساب. يمكنك المتابعة واستخدام التطبيق كاملًا حتى عند الرفض.") },
            confirmButton = { TextButton(onClick = { showPermissionExplanation = false; onNotificationsPermission() }) { Text("متابعة") } },
            dismissButton = { TextButton(onClick = { showPermissionExplanation = false }) { Text("ليس الآن") } },
        )
    }

    LaunchedEffect(state) {
        if (state == HomeUiState.SessionExpired) onLogout()
    }

    val currentData = when (val current = state) {
        is HomeUiState.Content -> current.data
        is HomeUiState.Error -> current.previousData
        HomeUiState.Loading,
        HomeUiState.SessionExpired,
        -> null
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MasaryColors.background,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                StudentBottomBar(
                    selected = destination,
                    onSelected = { target -> studentNavController.navigate(target) { launchSingleTop = true; popUpTo(StudentDestination.Home) { saveState = true }; restoreState = true } },
                )
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MasaryColors.background),
            ) {
                NavHost(studentNavController, startDestination = StudentDestination.Home) {
                    composable<StudentDestination.Home> { HomeStateContent(
                        session = session,
                        state = state,
                        onRefresh = homeViewModel::refresh,
                        onNotifications = {
                            when (permissionState) {
                                NotificationPermissionState.NotRequested,
                                NotificationPermissionState.Denied,
                                -> showPermissionExplanation = true
                                NotificationPermissionState.PermanentlyDenied -> onOpenNotificationSettings()
                                NotificationPermissionState.SystemDisabled -> onOpenNotificationSettings()
                                else -> Unit
                            }
                        },
                        onBrowseSubjects = { studentNavController.navigate(StudentDestination.Subjects) },
                    ) }
                    composable<StudentDestination.Subjects> { DataDestination(currentData) { data -> SubjectsSection(data) { studentNavController.navigate(StudentDestination.SubjectDetails(it)) } } }
                    composable<StudentDestination.Guide> { DataDestination(currentData) { SmartGuideCard(it.smartGuide) { step -> studentNavController.navigate(StudentDestination.SubjectDetails(step.subjectVersionId)) } } }
                    composable<StudentDestination.Ranking> { DataDestination(currentData) { RankingSection(it) } }
                    composable<StudentDestination.Profile> { ProfileSection(
                        session = session,
                        data = currentData,
                        onLogout = onLogout,
                    ) }
                    composable<StudentDestination.SubjectDetails> { backStackEntry ->
                        val id = backStackEntry.toRoute<StudentDestination.SubjectDetails>().subjectVersionId
                        DataDestination(currentData) { data -> SubjectDetailsSection(data, id) }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeStateContent(
    session: StudentSession,
    state: HomeUiState,
    onRefresh: () -> Unit,
    onNotifications: () -> Unit,
    onBrowseSubjects: () -> Unit,
) {
    when (state) {
        HomeUiState.Loading,
        HomeUiState.SessionExpired,
        -> LoadingHome()

        is HomeUiState.Content -> HomeContent(
            session = session,
            data = state.data,
            isRefreshing = state.isRefreshing,
            errorMessage = state.refreshMessage,
            onRefresh = onRefresh,
            onNotifications = onNotifications,
            onBrowseSubjects = onBrowseSubjects,
        )

        is HomeUiState.Error -> {
            val previousData = state.previousData
            if (previousData != null) {
                HomeContent(
                    session = session,
                    data = previousData,
                    isRefreshing = false,
                    errorMessage = state.message,
                    onRefresh = onRefresh,
                    onNotifications = onNotifications,
                    onBrowseSubjects = onBrowseSubjects,
                )
            } else {
                HomeError(message = state.message, onRetry = onRefresh)
            }
        }
    }
}

@Composable
private fun LoadingHome() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MasaryColors.brandNavyDeep)
            .statusBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            MasaryBrandMark(size = 84.dp)
            Spacer(Modifier.height(22.dp))
            CircularProgressIndicator(
                color = MasaryColors.brandGoldBright,
                trackColor = Color.White.copy(alpha = 0.15f),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.home_loading),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.9f),
            )
        }
    }
}

@Composable
private fun HomeError(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                MasaryBrandMark(size = 70.dp)
                Spacer(Modifier.height(18.dp))
                Text(
                    text = stringResource(R.string.home_load_error),
                    style = MaterialTheme.typography.titleLarge,
                    color = MasaryColors.brandNavy,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MasaryColors.muted,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MasaryColors.brandGoldBright,
                        contentColor = MasaryColors.brandNavyDeep,
                    ),
                ) {
                    Text(stringResource(R.string.home_retry), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun HomeContent(
    session: StudentSession,
    data: StudentHomeData,
    isRefreshing: Boolean,
    errorMessage: String?,
    onRefresh: () -> Unit,
    onNotifications: () -> Unit,
    onBrowseSubjects: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            BrandHomeHeader(
                displayName = data.student.displayName.ifBlank { session.displayName },
                gems = data.summary.gems,
                unreadCount = data.notifications.unreadCount,
                subscriptionActive = data.subscription.active,
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                onNotifications = onNotifications,
            )
        }
        if (!errorMessage.isNullOrBlank()) {
            item {
                Box(Modifier.padding(horizontal = 18.dp)) {
                    ErrorBanner(errorMessage, onRefresh)
                }
            }
        }
        if (data.snapshot != null) {
            item {
                Box(Modifier.padding(horizontal = 18.dp)) {
                    ErrorBanner("أنت تعرض آخر نسخة محفوظة. حدّث الصفحة عند عودة الاتصال.", onRefresh)
                }
            }
        }
        item {
            Box(Modifier.padding(horizontal = 18.dp)) {
                LevelHeroCard(data)
            }
        }
        item {
            Box(Modifier.padding(horizontal = 18.dp)) {
                QuickMetrics(data)
            }
        }
        item {
            Box(Modifier.padding(horizontal = 18.dp)) {
                SubjectsPreview(data, onBrowseSubjects)
            }
        }
        data.spotlight?.let { spotlight -> item { Box(Modifier.padding(horizontal = 18.dp)) { SpotlightCard(spotlight) } } }
        item {
            Box(Modifier.padding(horizontal = 18.dp)) {
                ContinueJourneyCard(data, onBrowseSubjects)
            }
        }
        item {
            Box(Modifier.padding(horizontal = 18.dp)) {
                StreakStatusCard(data)
            }
        }
    }
}

@Composable
private fun BrandHomeHeader(
    displayName: String,
    gems: Int,
    unreadCount: Int,
    subscriptionActive: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onNotifications: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp))
            .background(
                Brush.verticalGradient(
                    listOf(MasaryColors.brandNavyDeep, MasaryColors.brandNavy),
                ),
            )
            .statusBarsPadding()
            .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 24.dp),
    ) {
        MasaryOrbitDecoration(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(118.dp),
        )
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MasaryBrandLockup(logoSize = 46.dp, inverse = true)
                Spacer(Modifier.weight(1f))
                GemPill(gems)
                Spacer(Modifier.width(8.dp))
                NotificationButton(unreadCount, onNotifications)
            }
            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StudentAvatar(displayName)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.home_greeting, shortDisplayName(displayName)),
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.home_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.72f),
                    )
                    Spacer(Modifier.height(7.dp))
                    SubscriptionPill(subscriptionActive)
                }
                IconButton(onClick = onRefresh, enabled = !isRefreshing) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = MasaryColors.brandGoldBright,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = stringResource(R.string.home_refresh),
                            tint = MasaryColors.brandGoldBright,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GemPill(gems: Int) {
    Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.12f)) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Diamond,
                contentDescription = null,
                tint = MasaryColors.brandGoldBright,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = gems.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun NotificationButton(unreadCount: Int, onNotifications: () -> Unit) {
    BadgedBox(
        badge = {
            if (unreadCount > 0) {
                Badge(containerColor = MasaryColors.error) {
                    Text(if (unreadCount > 99) "99+" else unreadCount.toString())
                }
            }
        },
    ) {
        Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.12f)) {
            IconButton(onClick = onNotifications) {
                Icon(
                    imageVector = Icons.Outlined.NotificationsNone,
                    contentDescription = stringResource(R.string.home_notifications),
                    tint = Color.White,
                )
            }
        }
    }
}

@Composable
private fun SubscriptionPill(active: Boolean) {
    Surface(
        shape = CircleShape,
        color = if (active) MasaryColors.success.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.10f),
    ) {
        Text(
            text = stringResource(
                if (active) R.string.home_subscription_active else R.string.home_subscription_inactive,
            ),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (active) Color(0xFF8EF0BC) else Color.White.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun StudentAvatar(displayName: String) {
    Box(
        modifier = Modifier
            .size(58.dp)
            .clip(CircleShape)
            .background(MasaryColors.brandGoldBright),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = displayName.trim().firstOrNull()?.toString() ?: "م",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = MasaryColors.brandNavyDeep,
        )
    }
}

@Composable
private fun ErrorBanner(message: String, onRetry: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = Color(0xFFFFE8E5),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF8B241C),
            )
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.home_retry), color = MasaryColors.brandNavy)
            }
        }
    }
}

@Composable
private fun LevelHeroCard(data: StudentHomeData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MasaryColors.brandNavy),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(MasaryColors.brandNavyDeep, MasaryColors.brandNavy, Color(0xFF123776)),
                    ),
                )
                .padding(20.dp),
        ) {
            MasaryOrbitDecoration(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(width = 150.dp, height = 85.dp),
            )
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.home_your_points),
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White.copy(alpha = 0.72f),
                        )
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = data.summary.globalXp.toString(),
                                style = MaterialTheme.typography.displayMedium,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.home_points_label),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.72f),
                                modifier = Modifier.padding(bottom = 7.dp),
                            )
                        }
                    }
                    Surface(shape = CircleShape, color = MasaryColors.brandGoldBright) {
                        Icon(
                            imageVector = Icons.Outlined.EmojiEvents,
                            contentDescription = null,
                            tint = MasaryColors.brandNavyDeep,
                            modifier = Modifier.padding(16.dp).size(30.dp),
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.home_level_value, data.summary.level),
                        style = MaterialTheme.typography.titleMedium,
                        color = MasaryColors.brandGoldBright,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "${data.summary.levelPercent}%",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                    )
                }
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { data.summary.levelPercent.coerceIn(0, 100) / 100f },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                    color = MasaryColors.brandGoldBright,
                    trackColor = Color.White.copy(alpha = 0.16f),
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    text = stringResource(R.string.home_next_level, data.summary.levelNextXp),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.65f),
                )
            }
        }
    }
}

@Composable
private fun QuickMetrics(data: StudentHomeData) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        BrandedMetric(
            modifier = Modifier.weight(1f),
            icon = Icons.Outlined.EmojiEvents,
            value = data.indicators.totalXp.toString(),
            label = stringResource(R.string.home_xp),
            accent = MasaryColors.brandGoldBright,
        )
        BrandedMetric(
            modifier = Modifier.weight(1f),
            icon = Icons.Outlined.Diamond,
            value = data.indicators.gems.toString(),
            label = stringResource(R.string.home_gems),
            accent = MasaryColors.brandGoldBright,
        )
        BrandedMetric(
            modifier = Modifier.weight(1f),
            icon = Icons.Outlined.LocalFireDepartment,
            value = data.indicators.streakDays.toString(),
            label = stringResource(R.string.home_streak),
            accent = MasaryColors.warning,
        )
        BrandedMetric(
            modifier = Modifier.weight(1f),
            icon = Icons.Outlined.EmojiEvents,
            value = data.indicators.globalRank?.toString() ?: "—",
            label = stringResource(R.string.nav_ranking),
            accent = MasaryColors.info,
        )
    }
}

@Composable
private fun BrandedMetric(
    modifier: Modifier,
    icon: ImageVector,
    value: String,
    label: String,
    accent: Color,
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 13.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(shape = CircleShape, color = accent.copy(alpha = 0.13f)) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.padding(7.dp).size(20.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = MasaryColors.brandNavy,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MasaryColors.muted,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SmartGuideCard(
    guide: HomeSmartGuide,
    onGuideStep: (HomeSmartGuideStep) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MasaryColors.iceSurface)
                    .padding(18.dp),
            ) {
                MasaryOrbitDecoration(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(width = 150.dp, height = 74.dp),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = MasaryColors.brandGoldBright) {
                        Icon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            tint = MasaryColors.brandNavyDeep,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.home_smart_guide),
                            style = MaterialTheme.typography.labelLarge,
                            color = MasaryColors.brandGold,
                        )
                        Text(
                            text = guide.headline.ifBlank { stringResource(R.string.home_smart_guide) },
                            style = MaterialTheme.typography.titleLarge,
                            color = MasaryColors.brandNavy,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (guide.totalSteps > 0) {
                        Surface(shape = CircleShape, color = MasaryColors.brandNavy) {
                            Text(
                                text = "${guide.completionPercent}%",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White,
                            )
                        }
                    }
                }
            }

            Column(modifier = Modifier.padding(18.dp)) {
                if (guide.introText.isNotBlank()) {
                    Text(
                        text = guide.introText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MasaryColors.muted,
                    )
                    Spacer(Modifier.height(12.dp))
                }
                if (guide.totalSteps > 0) {
                    LinearProgressIndicator(
                        progress = { guide.completionPercent.coerceIn(0, 100) / 100f },
                        modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape),
                        color = MasaryColors.brandGoldBright,
                        trackColor = MasaryColors.border,
                    )
                    Spacer(Modifier.height(7.dp))
                    Text(
                        text = stringResource(
                            R.string.home_guide_progress,
                            guide.completedSteps,
                            guide.totalSteps,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MasaryColors.muted,
                    )
                }
                if (guide.steps.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    val nextStep = guide.nextPendingStep
                    guide.steps.forEachIndexed { index, step ->
                        GuideStepRow(
                            step = step,
                            isNext = step.id == nextStep?.id,
                            onClick = { onGuideStep(step) },
                        )
                        if (index < guide.steps.lastIndex) Spacer(Modifier.height(10.dp))
                    }
                } else {
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MasaryColors.warmSurface,
                    ) {
                        Text(
                            text = guide.boostNote.ifBlank { stringResource(R.string.home_guide_waiting) },
                            modifier = Modifier.padding(14.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MasaryColors.brandNavy,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideStepRow(
    step: HomeSmartGuideStep,
    isNext: Boolean,
    onClick: () -> Unit,
) {
    val completed = step.progressState == "completed"
    val container = when {
        completed -> MasaryColors.success.copy(alpha = 0.09f)
        isNext -> MasaryColors.warmSurface
        else -> MasaryColors.background
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = container,
        border = BorderStroke(
            1.dp,
            when {
                completed -> MasaryColors.success.copy(alpha = 0.28f)
                isNext -> MasaryColors.brandGold.copy(alpha = 0.45f)
                else -> MasaryColors.border
            },
        ),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Surface(
                    shape = CircleShape,
                    color = if (completed) {
                        MasaryColors.success.copy(alpha = 0.14f)
                    } else {
                        MasaryColors.brandGoldBright.copy(alpha = 0.2f)
                    },
                ) {
                    Icon(
                        imageVector = if (completed) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (completed) MasaryColors.success else MasaryColors.brandGold,
                        modifier = Modifier.padding(7.dp).size(20.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = step.title.ifBlank { step.ctaLabel },
                        style = MaterialTheme.typography.titleMedium,
                        color = MasaryColors.brandNavy,
                    )
                    val detail = step.subtitle.ifBlank { step.subjectName }
                    if (detail.isNotBlank()) {
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MasaryColors.muted,
                        )
                    }
                }
                Text(
                    text = stringResource(
                        if (completed) R.string.home_guide_completed else R.string.home_guide_pending,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (completed) MasaryColors.success else MasaryColors.brandGold,
                )
            }
            if (step.reasonText.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = step.reasonText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MasaryColors.muted,
                )
            }
            Spacer(Modifier.height(11.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (step.estimatedMinutes > 0) {
                    GuideChip(
                        icon = Icons.Outlined.Timer,
                        text = stringResource(R.string.home_guide_minutes, step.estimatedMinutes),
                    )
                }
                if (step.estimatedMinutes > 0 && step.rewardGems > 0) Spacer(Modifier.width(7.dp))
                if (step.rewardGems > 0) {
                    GuideChip(
                        icon = Icons.Outlined.Diamond,
                        text = stringResource(R.string.home_guide_reward, step.rewardGems),
                    )
                }
                Spacer(Modifier.weight(1f))
                if (isNext && !completed) {
                    Button(
                        onClick = onClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MasaryColors.brandGoldBright,
                            contentColor = MasaryColors.brandNavyDeep,
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 9.dp),
                    ) {
                        Text(step.ctaLabel.ifBlank { "ابدأ الآن" }, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideChip(icon: ImageVector, text: String) {
    Surface(shape = CircleShape, color = MasaryColors.iceSurface) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MasaryColors.brandNavy,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = MasaryColors.brandNavy,
            )
        }
    }
}

@Composable
private fun DataDestination(data: StudentHomeData?, content: @Composable (StudentHomeData) -> Unit) {
    if (data == null) LoadingHome() else LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(18.dp),
    ) { item { content(data) } }
}

@Composable
private fun SubjectsPreview(data: StudentHomeData, onOpen: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(18.dp)) {
            SectionHeader(stringResource(R.string.nav_subjects), Icons.AutoMirrored.Outlined.MenuBook)
            data.subjects.take(3).forEach { subject ->
                Text(subject.name, modifier = Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleMedium)
                subject.progressPercent?.let { progress ->
                    LinearProgressIndicator({ progress / 100f }, Modifier.fillMaxWidth().padding(top = 5.dp))
                }
            }
            if (data.subjects.isEmpty()) Text(stringResource(R.string.home_no_subjects), Modifier.padding(top = 12.dp), color = MasaryColors.muted)
            TextButton(onClick = onOpen, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.home_browse_subjects)) }
        }
    }
}

@Composable
private fun SubjectsSection(data: StudentHomeData, onSubject: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.nav_subjects), style = MaterialTheme.typography.headlineMedium, color = MasaryColors.brandNavy)
        data.subjects.forEach { subject -> Card(modifier = Modifier.clickable { onSubject(subject.subjectVersionId) }, colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(subject.name, style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.home_hearts, subject.hearts), color = MasaryColors.muted)
                subject.progressPercent?.let { progress ->
                    LinearProgressIndicator({ progress / 100f }, Modifier.fillMaxWidth().padding(top = 8.dp))
                }
            }
        } }
        if (data.subjects.isEmpty()) Text(stringResource(R.string.home_no_subjects), color = MasaryColors.muted)
    }
}

@Composable
private fun SubjectDetailsSection(data: StudentHomeData, subjectVersionId: Int) {
    val subject = data.subjects.firstOrNull { it.subjectVersionId == subjectVersionId }
    if (subject == null) {
        Text(stringResource(R.string.home_subject_unavailable), color = MasaryColors.muted)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(subject.name, style = MaterialTheme.typography.headlineMedium, color = MasaryColors.brandNavy)
        Text(stringResource(R.string.home_hearts, subject.hearts), color = MasaryColors.muted)
        subject.progressPercent?.let { progress -> LinearProgressIndicator({ progress / 100f }, Modifier.fillMaxWidth()) }
    }
}

@Composable
private fun SpotlightCard(item: HomeSpotlight) {
    val uriHandler = LocalUriHandler.current
    val safeUrl = item.ctaUrl.takeIf { it.startsWith("https://") }
    Card(colors = CardDefaults.cardColors(containerColor = MasaryColors.warmSurface)) { Column(Modifier.fillMaxWidth().padding(18.dp)) {
        Text(if (item.type == "offer") stringResource(R.string.home_offer) else stringResource(R.string.home_news), color = MasaryColors.brandGold)
        Text(item.title, style = MaterialTheme.typography.titleLarge, color = MasaryColors.brandNavy)
        if (item.body.isNotBlank()) Text(item.body, color = MasaryColors.muted)
        if (safeUrl != null && item.ctaLabel.isNotBlank()) {
            TextButton(onClick = { uriHandler.openUri(safeUrl) }, modifier = Modifier.align(Alignment.End)) {
                Text(item.ctaLabel)
            }
        }
    } }
}

@Composable
private fun RankingSection(data: StudentHomeData) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Outlined.EmojiEvents, null, Modifier.size(72.dp), tint = MasaryColors.brandGold)
        Text(stringResource(R.string.nav_ranking), style = MaterialTheme.typography.headlineMedium)
        Text(data.indicators.globalRank?.let { stringResource(R.string.home_rank_value, it) } ?: stringResource(R.string.home_rank_unavailable), style = MaterialTheme.typography.titleLarge, color = MasaryColors.brandNavy)
    }
}

@Composable
private fun ContinueJourneyCard(
    data: StudentHomeData,
    onBrowseSubjects: () -> Unit,
) {
    val item = data.continueLearning
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            SectionHeader(
                title = stringResource(R.string.home_continue),
                icon = Icons.Outlined.School,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = if (item.available) item.subjectName else item.label,
                style = MaterialTheme.typography.titleLarge,
                color = MasaryColors.brandNavy,
            )
            val detail = when {
                item.available && item.unitTitle.isNotBlank() -> item.unitTitle
                item.hint.isNotBlank() -> item.hint
                else -> stringResource(R.string.home_no_last_activity)
            }
            Spacer(Modifier.height(5.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MasaryColors.muted,
            )
            if (item.hearts != null) {
                Spacer(Modifier.height(10.dp))
                Surface(shape = CircleShape, color = MasaryColors.warmSurface) {
                    Text(
                        text = stringResource(R.string.home_hearts, item.hearts),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MasaryColors.brandNavy,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onBrowseSubjects,
                enabled = !item.disabled,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MasaryColors.brandGoldBright,
                    contentColor = MasaryColors.brandNavyDeep,
                ),
            ) {
                Text(
                    text = if (item.available) {
                        item.label.ifBlank { stringResource(R.string.home_continue) }
                    } else {
                        stringResource(R.string.home_browse_subjects)
                    },
                    fontWeight = FontWeight.Bold,
                )
            }
            if (item.disabled && item.disabledReason.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = item.disabledReason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MasaryColors.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun StreakStatusCard(data: StudentHomeData) {
    val streak = data.streak
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MasaryColors.brandNavy),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MasaryColors.brandGoldBright.copy(alpha = 0.18f)) {
                    Icon(
                        imageVector = Icons.Outlined.LocalFireDepartment,
                        contentDescription = null,
                        tint = MasaryColors.brandGoldBright,
                        modifier = Modifier.padding(9.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.home_streak_status),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                    Text(
                        text = streak.message.ifBlank {
                            stringResource(R.string.home_streak_best, streak.bestDays)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                }
                Text(
                    text = streak.currentDays.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MasaryColors.brandGoldBright,
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DarkInfoChip(stringResource(R.string.home_streak_best, streak.bestDays))
                DarkInfoChip(
                    stringResource(
                        R.string.home_streak_shields,
                        streak.protectionCount,
                        streak.protectionMax,
                    ),
                )
            }
            if (streak.goal.days > 0) {
                Spacer(Modifier.height(14.dp))
                LinearProgressIndicator(
                    progress = { streak.goal.progressPercent.coerceIn(0, 100) / 100f },
                    modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape),
                    color = MasaryColors.brandGoldBright,
                    trackColor = Color.White.copy(alpha = 0.15f),
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    text = stringResource(
                        R.string.home_streak_goal_remaining,
                        streak.goal.remainingDays,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.72f),
                )
            }
        }
    }
}

@Composable
private fun DarkInfoChip(text: String) {
    Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.10f)) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.82f),
        )
    }
}

@Composable
private fun SectionHeader(title: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = MasaryColors.iceSurface) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MasaryColors.brandNavy,
                modifier = Modifier.padding(8.dp).size(20.dp),
            )
        }
        Spacer(Modifier.width(9.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MasaryColors.brandNavy,
        )
    }
}

@Composable
private fun ComingSoonSection(destination: StudentDestination) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        ) {
            Column(
                modifier = Modifier.padding(26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                MasaryBrandMark(size = 70.dp)
                Spacer(Modifier.height(18.dp))
                Icon(
                    imageVector = destination.icon,
                    contentDescription = null,
                    tint = MasaryColors.brandGold,
                    modifier = Modifier.size(42.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(
                        R.string.coming_soon_title,
                        stringResource(destination.labelRes),
                    ),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MasaryColors.brandNavy,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.coming_soon_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MasaryColors.muted,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun ProfileSection(
    session: StudentSession,
    data: StudentHomeData?,
    onLogout: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp),
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(MasaryColors.brandNavyDeep, MasaryColors.brandNavy),
                        ),
                    )
                    .statusBarsPadding()
                    .padding(24.dp),
            ) {
                MasaryOrbitDecoration(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(130.dp),
                )
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    MasaryBrandMark(size = 66.dp)
                    Spacer(Modifier.height(14.dp))
                    StudentAvatar(data?.student?.displayName ?: session.displayName)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = data?.student?.displayName?.ifBlank { session.displayName }
                            ?: session.displayName,
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = stringResource(R.string.profile_username, session.username),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                    if (data != null) {
                        Spacer(Modifier.height(12.dp))
                        Surface(shape = CircleShape, color = MasaryColors.brandGoldBright) {
                            Text(
                                text = stringResource(R.string.home_level_value, data.summary.level),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelLarge,
                                color = MasaryColors.brandNavyDeep,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
        item {
            Column(modifier = Modifier.padding(18.dp)) {
                if (data != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        ProfileMetric(
                            Modifier.weight(1f),
                            data.summary.globalXp.toString(),
                            stringResource(R.string.home_points_label),
                        )
                        ProfileMetric(
                            Modifier.weight(1f),
                            data.summary.gems.toString(),
                            stringResource(R.string.home_gems),
                        )
                        ProfileMetric(
                            Modifier.weight(1f),
                            data.streak.currentDays.toString(),
                            stringResource(R.string.home_streak),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = stringResource(R.string.profile_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = MasaryColors.brandNavy,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.profile_session_secure),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MasaryColors.muted,
                        )
                        if (data != null) {
                            Spacer(Modifier.height(12.dp))
                            HorizontalDivider(color = MasaryColors.border)
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = stringResource(
                                    R.string.profile_total_points,
                                    data.summary.globalXp,
                                ),
                                style = MaterialTheme.typography.titleMedium,
                                color = MasaryColors.brandNavy,
                            )
                        }
                        Spacer(Modifier.height(20.dp))
                        OutlinedButton(
                            onClick = onLogout,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MasaryColors.brandNavy,
                            ),
                        ) {
                            Text(stringResource(R.string.logout), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileMetric(modifier: Modifier, value: String, label: String) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = MasaryColors.brandNavy,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MasaryColors.muted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun StudentBottomBar(
    selected: StudentDestination,
    onSelected: (StudentDestination) -> Unit,
) {
    NavigationBar(
        modifier = Modifier.navigationBarsPadding(),
        containerColor = MasaryColors.brandNavyDeep,
        tonalElevation = 0.dp,
    ) {
        studentDestinations.forEach { destination ->
            NavigationBarItem(
                selected = destination == selected,
                onClick = { onSelected(destination) },
                icon = {
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = stringResource(destination.labelRes),
                    )
                },
                label = { Text(stringResource(destination.labelRes)) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MasaryColors.brandNavyDeep,
                    selectedTextColor = MasaryColors.brandGoldBright,
                    indicatorColor = MasaryColors.brandGoldBright,
                    unselectedIconColor = Color.White.copy(alpha = 0.68f),
                    unselectedTextColor = Color.White.copy(alpha = 0.68f),
                ),
            )
        }
    }
}

internal fun shortDisplayName(displayName: String): String {
    val names = displayName.trim().split(Regex("\\s+")).filter(String::isNotBlank)
    return names.take(2).joinToString(" ").ifBlank { "طالب مساري" }
}
