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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Diamond
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge as MaterialBadge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.masary.core.models.auth.StudentSession
import app.masary.core.ui.MasaryBrandLockup
import app.masary.core.ui.MasaryBrandMark
import app.masary.core.ui.MasaryColors
import app.masary.core.ui.MasaryOrbitDecoration
import app.masary.feature.home.R
import app.masary.feature.home.domain.HomeRepository
import app.masary.feature.home.domain.HomeSmartGuide
import app.masary.feature.home.domain.HomeSmartGuideStep
import app.masary.feature.home.domain.HomeSpotlight
import app.masary.feature.home.domain.HomeSubject
import app.masary.feature.home.domain.StudentHomeData
import app.masary.feature.notifications.NotificationPermissionState

private val StudentDestination.icon: ImageVector
    get() = when (this) {
        StudentDestination.Home -> Icons.Outlined.Home
        StudentDestination.Guide -> Icons.Outlined.AutoAwesome
        StudentDestination.Subjects -> Icons.AutoMirrored.Outlined.MenuBook
        StudentDestination.Ranking -> Icons.Outlined.EmojiEvents
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
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val selectedDestination = when {
        backStack?.destination?.hasRoute<StudentDestination.Guide>() == true -> StudentDestination.Guide
        backStack?.destination?.hasRoute<StudentDestination.Subjects>() == true ||
            backStack?.destination?.hasRoute<StudentDestination.SubjectDetails>() == true -> StudentDestination.Subjects
        backStack?.destination?.hasRoute<StudentDestination.Ranking>() == true -> StudentDestination.Ranking
        backStack?.destination?.hasRoute<StudentDestination.Profile>() == true -> StudentDestination.Profile
        else -> StudentDestination.Home
    }
    var showPermissionExplanation by rememberSaveable {
        mutableStateOf(permissionState == NotificationPermissionState.NotRequested)
    }

    fun navigateTo(destination: StudentDestination) {
        navController.navigate(destination) {
            launchSingleTop = true
            popUpTo(StudentDestination.Home) { saveState = true }
            restoreState = true
        }
    }

    LaunchedEffect(externalDestination) {
        externalDestination?.let {
            navigateTo(externalStudentDestination(it))
            onExternalDestinationConsumed()
        }
    }
    LaunchedEffect(state) {
        if (state == HomeUiState.SessionExpired) onLogout()
    }

    if (showPermissionExplanation) {
        AlertDialog(
            onDismissRequest = { showPermissionExplanation = false },
            title = { Text(stringResource(R.string.notification_permission_title)) },
            text = { Text(stringResource(R.string.notification_permission_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPermissionExplanation = false
                        onNotificationsPermission()
                    },
                ) { Text(stringResource(R.string.continue_label)) }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionExplanation = false }) {
                    Text(stringResource(R.string.not_now))
                }
            },
        )
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
            bottomBar = {
                StudentBottomBar(
                    selected = selectedDestination,
                    onSelected = ::navigateTo,
                )
            },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = StudentDestination.Home,
                modifier = Modifier.padding(innerPadding),
            ) {
                composable<StudentDestination.Home> {
                    HomeStateContent(
                        session = session,
                        state = state,
                        onRefresh = homeViewModel::refresh,
                        onNotifications = {
                            when (permissionState) {
                                NotificationPermissionState.NotRequested,
                                NotificationPermissionState.Denied,
                                -> showPermissionExplanation = true
                                NotificationPermissionState.PermanentlyDenied,
                                NotificationPermissionState.SystemDisabled,
                                -> onOpenNotificationSettings()
                                else -> Unit
                            }
                        },
                        onGems = { navigateTo(StudentDestination.Profile) },
                        onOpenGuide = { navigateTo(StudentDestination.Guide) },
                        onGuideStep = { step ->
                            navigateTo(
                                StudentDestination.SubjectDetails(
                                    subjectVersionId = step.subjectVersionId,
                                    unitId = step.unitId,
                                    actionKey = step.actionKey,
                                ),
                            )
                        },
                        onBrowseSubjects = { navigateTo(StudentDestination.Subjects) },
                        onSubject = { navigateTo(StudentDestination.SubjectDetails(it)) },
                    )
                }
                composable<StudentDestination.Guide> {
                    DataDestination(currentData) { data ->
                        GuideSection(
                            guide = data.smartGuide,
                            onStep = { step ->
                                navigateTo(
                                    StudentDestination.SubjectDetails(
                                        subjectVersionId = step.subjectVersionId,
                                        unitId = step.unitId,
                                        actionKey = step.actionKey,
                                    ),
                                )
                            },
                        )
                    }
                }
                composable<StudentDestination.Subjects> {
                    DataDestination(currentData) { data ->
                        SubjectsSection(data.subjects) {
                            navigateTo(StudentDestination.SubjectDetails(it))
                        }
                    }
                }
                composable<StudentDestination.Ranking> {
                    DataDestination(currentData) { RankingSection(it) }
                }
                composable<StudentDestination.Profile> {
                    ProfileSection(session = session, data = currentData, onLogout = onLogout)
                }
                composable<StudentDestination.SubjectDetails> { entry ->
                    val destination = entry.toRoute<StudentDestination.SubjectDetails>()
                    DataDestination(currentData) { data ->
                        SubjectDetailsSection(data, destination)
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
    onGems: () -> Unit,
    onOpenGuide: () -> Unit,
    onGuideStep: (HomeSmartGuideStep) -> Unit,
    onBrowseSubjects: () -> Unit,
    onSubject: (Int) -> Unit,
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
            onGems = onGems,
            onOpenGuide = onOpenGuide,
            onGuideStep = onGuideStep,
            onBrowseSubjects = onBrowseSubjects,
            onSubject = onSubject,
        )
        is HomeUiState.Error -> {
            val previous = state.previousData
            if (previous == null) {
                HomeError(state.message, onRefresh)
            } else {
                HomeContent(
                    session = session,
                    data = previous,
                    isRefreshing = false,
                    errorMessage = state.message,
                    onRefresh = onRefresh,
                    onNotifications = onNotifications,
                    onGems = onGems,
                    onOpenGuide = onOpenGuide,
                    onGuideStep = onGuideStep,
                    onBrowseSubjects = onBrowseSubjects,
                    onSubject = onSubject,
                )
            }
        }
    }
}

@Composable
private fun LoadingHome() {
    Box(
        modifier = Modifier.fillMaxSize().background(MasaryColors.brandNavyDeep),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            MasaryBrandMark(size = 84.dp)
            Spacer(Modifier.height(22.dp))
            CircularProgressIndicator(color = MasaryColors.brandGoldBright)
            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.home_loading), color = Color.White)
        }
    }
}

@Composable
private fun HomeError(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().statusBarsPadding().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                MasaryBrandMark(size = 70.dp)
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.home_load_error),
                    style = MaterialTheme.typography.titleLarge,
                    color = MasaryColors.brandNavy,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(message, color = MasaryColors.muted, textAlign = TextAlign.Center)
                Spacer(Modifier.height(18.dp))
                Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.home_retry))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeContent(
    session: StudentSession,
    data: StudentHomeData,
    isRefreshing: Boolean,
    errorMessage: String?,
    onRefresh: () -> Unit,
    onNotifications: () -> Unit,
    onGems: () -> Unit,
    onOpenGuide: () -> Unit,
    onGuideStep: (HomeSmartGuideStep) -> Unit,
    onBrowseSubjects: () -> Unit,
    onSubject: (Int) -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
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
                    onGems = onGems,
                    onNotifications = onNotifications,
                )
            }
            if (!errorMessage.isNullOrBlank()) {
                item { Padded { StatusBanner(errorMessage) } }
            }
            data.snapshot?.let { snapshot ->
                item {
                    Padded {
                        StatusBanner(
                            stringResource(
                                R.string.home_cached_snapshot,
                                formatSnapshotAge(snapshot.savedAtEpochMillis),
                            ),
                        )
                    }
                }
            }
            item { Padded { LevelHeroCard(data) } }
            item { Padded { QuickMetrics(data) } }
            item {
                Padded {
                    SmartGuideNextCard(
                        guide = data.smartGuide,
                        onOpenGuide = onOpenGuide,
                        onStep = onGuideStep,
                    )
                }
            }
            item {
                Padded {
                    SubjectsPreview(
                        subjects = data.subjects,
                        onOpenAll = onBrowseSubjects,
                        onSubject = onSubject,
                    )
                }
            }
            data.spotlight?.let { spotlight ->
                item { Padded { SpotlightCard(spotlight) } }
            }
        }
    }
}

@Composable
private fun Padded(content: @Composable () -> Unit) {
    Box(Modifier.padding(horizontal = 18.dp)) { content() }
}

@Composable
private fun BrandHomeHeader(
    displayName: String,
    gems: Int,
    unreadCount: Int,
    subscriptionActive: Boolean,
    onGems: () -> Unit,
    onNotifications: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp))
            .background(Brush.verticalGradient(listOf(MasaryColors.brandNavyDeep, MasaryColors.brandNavy)))
            .statusBarsPadding()
            .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 24.dp),
    ) {
        MasaryOrbitDecoration(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().height(118.dp),
        )
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MasaryBrandLockup(logoSize = 46.dp, inverse = true)
                Spacer(Modifier.weight(1f))
                GemPill(gems, onGems)
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
                    Text(stringResource(R.string.home_subtitle), color = Color.White.copy(alpha = 0.72f))
                    Spacer(Modifier.height(7.dp))
                    SubscriptionPill(subscriptionActive)
                }
            }
        }
    }
}

@Composable
private fun GemPill(gems: Int, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.12f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Diamond, null, tint = MasaryColors.brandGoldBright, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(5.dp))
            Text(gems.toString(), color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(3.dp))
            Icon(Icons.Outlined.Add, stringResource(R.string.home_open_gems), tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun NotificationButton(unreadCount: Int, onClick: () -> Unit) {
    BadgedBox(
        badge = {
            if (unreadCount > 0) {
                MaterialBadge(containerColor = MasaryColors.error) {
                    Text(if (unreadCount > 99) "99+" else unreadCount.toString())
                }
            }
        },
    ) {
        Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.12f)) {
            IconButton(onClick = onClick) {
                Icon(Icons.Outlined.NotificationsNone, stringResource(R.string.home_notifications), tint = Color.White)
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
            text = stringResource(if (active) R.string.home_subscription_active else R.string.home_subscription_inactive),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (active) Color(0xFF8EF0BC) else Color.White.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun StudentAvatar(displayName: String) {
    Box(
        modifier = Modifier.size(58.dp).clip(CircleShape).background(MasaryColors.brandGoldBright),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            displayName.trim().firstOrNull()?.toString() ?: "م",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = MasaryColors.brandNavyDeep,
        )
    }
}

@Composable
private fun StatusBanner(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MasaryColors.iceSurface,
        border = BorderStroke(1.dp, MasaryColors.border),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MasaryColors.brandNavy,
        )
    }
}

@Composable
private fun LevelHeroCard(data: StudentHomeData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MasaryColors.brandNavy),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.home_your_points), color = Color.White.copy(alpha = 0.72f))
                    Text(
                        data.summary.globalXp.toString(),
                        style = MaterialTheme.typography.displayMedium,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
                Surface(shape = CircleShape, color = MasaryColors.brandGoldBright) {
                    Icon(
                        Icons.Outlined.EmojiEvents,
                        null,
                        tint = MasaryColors.brandNavyDeep,
                        modifier = Modifier.padding(16.dp).size(30.dp),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Row {
                Text(stringResource(R.string.home_level_value, data.summary.level), color = MasaryColors.brandGoldBright)
                Spacer(Modifier.weight(1f))
                Text("${data.summary.levelPercent}%", color = Color.White)
            }
            Spacer(Modifier.height(7.dp))
            LinearProgressIndicator(
                progress = { data.summary.levelPercent.coerceIn(0, 100) / 100f },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                color = MasaryColors.brandGoldBright,
                trackColor = Color.White.copy(alpha = 0.16f),
            )
        }
    }
}

@Composable
private fun QuickMetrics(data: StudentHomeData) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MetricCard(
            Modifier.weight(1f),
            Icons.Outlined.Timer,
            data.today.minutes.toString(),
            stringResource(R.string.home_today_minutes),
        )
        MetricCard(
            Modifier.weight(1f),
            Icons.Outlined.LocalFireDepartment,
            data.streak.currentDays.toString(),
            stringResource(R.string.home_streak),
        )
        MetricCard(
            Modifier.weight(1f),
            Icons.Outlined.TaskAlt,
            stringResource(R.string.home_unavailable_short),
            stringResource(R.string.home_tasks),
        )
        MetricCard(
            Modifier.weight(1f),
            Icons.Outlined.Badge,
            stringResource(R.string.home_unavailable_short),
            stringResource(R.string.home_badges),
        )
    }
}

@Composable
private fun MetricCard(modifier: Modifier, icon: ImageVector, value: String, label: String) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, null, tint = MasaryColors.brandGold, modifier = Modifier.size(21.dp))
            Spacer(Modifier.height(6.dp))
            Text(value, fontWeight = FontWeight.ExtraBold, color = MasaryColors.brandNavy)
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MasaryColors.muted,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun SmartGuideNextCard(
    guide: HomeSmartGuide,
    onOpenGuide: () -> Unit,
    onStep: (HomeSmartGuideStep) -> Unit,
) {
    val step = guide.nextPendingStep
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            SectionHeader(stringResource(R.string.home_smart_guide), Icons.Outlined.AutoAwesome)
            Spacer(Modifier.height(12.dp))
            when {
                step != null -> {
                    Text(
                        step.title.ifBlank { step.subjectName },
                        style = MaterialTheme.typography.titleLarge,
                        color = MasaryColors.brandNavy,
                    )
                    val detail = step.subtitle.ifBlank { step.reasonText }
                    if (detail.isNotBlank()) {
                        Spacer(Modifier.height(5.dp))
                        Text(detail, color = MasaryColors.muted, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (step.estimatedMinutes > 0) {
                            InfoChip(Icons.Outlined.Timer, stringResource(R.string.home_guide_minutes, step.estimatedMinutes))
                        }
                        Spacer(Modifier.weight(1f))
                        Button(
                            onClick = { onStep(step) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MasaryColors.brandGoldBright,
                                contentColor = MasaryColors.brandNavyDeep,
                            ),
                        ) {
                            Text(step.ctaLabel.ifBlank { stringResource(R.string.start_now) }, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                guide.isComplete -> {
                    Text(stringResource(R.string.home_guide_all_done), color = MasaryColors.success)
                    TextButton(onClick = onOpenGuide, modifier = Modifier.align(Alignment.End)) {
                        Text(stringResource(R.string.home_open_guide))
                    }
                }
                else -> {
                    Text(
                        guide.headline.ifBlank { stringResource(R.string.home_guide_waiting) },
                        color = MasaryColors.muted,
                    )
                    TextButton(onClick = onOpenGuide, modifier = Modifier.align(Alignment.End)) {
                        Text(stringResource(R.string.home_open_guide))
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideSection(guide: HomeSmartGuide, onStep: (HomeSmartGuideStep) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.home_smart_guide),
            style = MaterialTheme.typography.headlineMedium,
            color = MasaryColors.brandNavy,
        )
        if (guide.steps.isEmpty()) {
            StatusBanner(guide.headline.ifBlank { stringResource(R.string.home_guide_waiting) })
        } else {
            guide.steps.forEach { step -> GuideStepCard(step, step == guide.nextPendingStep, onStep) }
        }
    }
}

@Composable
private fun GuideStepCard(step: HomeSmartGuideStep, isNext: Boolean, onStep: (HomeSmartGuideStep) -> Unit) {
    val completed = step.progressState == "completed"
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                completed -> MasaryColors.success.copy(alpha = 0.08f)
                isNext -> MasaryColors.warmSurface
                else -> Color.White
            },
        ),
        border = BorderStroke(1.dp, if (isNext) MasaryColors.brandGold else MasaryColors.border),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (completed) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                    null,
                    tint = if (completed) MasaryColors.success else MasaryColors.brandGold,
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(step.title.ifBlank { step.subjectName }, fontWeight = FontWeight.Bold, color = MasaryColors.brandNavy)
                    if (step.subtitle.isNotBlank()) Text(step.subtitle, color = MasaryColors.muted)
                }
            }
            if (isNext && !completed) {
                Spacer(Modifier.height(12.dp))
                Button(onClick = { onStep(step) }, modifier = Modifier.fillMaxWidth()) {
                    Text(step.ctaLabel.ifBlank { stringResource(R.string.start_now) })
                }
            }
        }
    }
}

@Composable
private fun InfoChip(icon: ImageVector, text: String) {
    Surface(shape = CircleShape, color = MasaryColors.iceSurface) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, modifier = Modifier.size(15.dp), tint = MasaryColors.brandNavy)
            Spacer(Modifier.width(5.dp))
            Text(text, style = MaterialTheme.typography.labelSmall, color = MasaryColors.brandNavy)
        }
    }
}

@Composable
private fun SubjectsPreview(
    subjects: List<HomeSubject>,
    onOpenAll: () -> Unit,
    onSubject: (Int) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionHeader(stringResource(R.string.nav_subjects), Icons.AutoMirrored.Outlined.MenuBook)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onOpenAll) { Text(stringResource(R.string.home_browse_subjects)) }
            }
            if (subjects.isEmpty()) {
                Text(stringResource(R.string.home_no_subjects), color = MasaryColors.muted)
            } else {
                subjects.take(3).forEach { subject ->
                    SubjectRow(subject, onSubject)
                }
            }
        }
    }
}

@Composable
private fun SubjectRow(subject: HomeSubject, onSubject: (Int) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSubject(subject.subjectVersionId) }
            .padding(vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(subject.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, color = MasaryColors.brandNavy)
            Text(stringResource(R.string.home_hearts, subject.hearts), style = MaterialTheme.typography.labelSmall, color = MasaryColors.muted)
        }
        subject.progressPercent?.let {
            Spacer(Modifier.height(5.dp))
            LinearProgressIndicator(
                progress = { it.coerceIn(0, 100) / 100f },
                modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
                color = MasaryColors.brandGold,
                trackColor = MasaryColors.border,
            )
        }
    }
}

@Composable
private fun DataDestination(data: StudentHomeData?, content: @Composable (StudentHomeData) -> Unit) {
    if (data == null) {
        LoadingHome()
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(18.dp),
        ) { item { content(data) } }
    }
}

@Composable
private fun SubjectsSection(subjects: List<HomeSubject>, onSubject: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.nav_subjects), style = MaterialTheme.typography.headlineMedium, color = MasaryColors.brandNavy)
        if (subjects.isEmpty()) {
            StatusBanner(stringResource(R.string.home_no_subjects))
        } else {
            subjects.forEach { subject ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onSubject(subject.subjectVersionId) },
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(subject.name, style = MaterialTheme.typography.titleLarge, color = MasaryColors.brandNavy)
                        Text(stringResource(R.string.home_hearts, subject.hearts), color = MasaryColors.muted)
                    }
                }
            }
        }
    }
}

@Composable
private fun SubjectDetailsSection(data: StudentHomeData, destination: StudentDestination.SubjectDetails) {
    val subject = data.subjects.firstOrNull { it.subjectVersionId == destination.subjectVersionId }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            subject?.name ?: stringResource(R.string.home_subject_unavailable),
            style = MaterialTheme.typography.headlineMedium,
            color = MasaryColors.brandNavy,
        )
        subject?.let { Text(stringResource(R.string.home_hearts, it.hearts), color = MasaryColors.muted) }
        if (destination.unitId != null || !destination.actionKey.isNullOrBlank()) {
            StatusBanner(
                stringResource(
                    R.string.home_typed_destination_ready,
                    destination.unitId?.toString() ?: "—",
                    destination.actionKey.orEmpty().ifBlank { "—" },
                ),
            )
        } else {
            StatusBanner(stringResource(R.string.coming_soon_body))
        }
    }
}

@Composable
private fun SpotlightCard(item: HomeSpotlight) {
    val uriHandler = LocalUriHandler.current
    val safeUrl = item.ctaUrl.takeIf { it.startsWith("https://") }
    Card(colors = CardDefaults.cardColors(containerColor = MasaryColors.warmSurface)) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Text(
                stringResource(if (item.type == "offer") R.string.home_offer else R.string.home_news),
                color = MasaryColors.brandGold,
            )
            Text(item.title, style = MaterialTheme.typography.titleLarge, color = MasaryColors.brandNavy)
            if (item.body.isNotBlank()) Text(item.body, color = MasaryColors.muted, maxLines = 4, overflow = TextOverflow.Ellipsis)
            if (safeUrl != null && item.ctaLabel.isNotBlank()) {
                TextButton(onClick = { uriHandler.openUri(safeUrl) }, modifier = Modifier.align(Alignment.End)) {
                    Text(item.ctaLabel)
                }
            }
        }
    }
}

@Composable
private fun RankingSection(data: StudentHomeData) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Outlined.EmojiEvents, null, modifier = Modifier.size(72.dp), tint = MasaryColors.brandGold)
        Text(stringResource(R.string.nav_ranking), style = MaterialTheme.typography.headlineMedium)
        Text(
            data.indicators.globalRank?.let { stringResource(R.string.home_rank_value, it) }
                ?: stringResource(R.string.home_rank_unavailable),
            color = MasaryColors.brandNavy,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ProfileSection(session: StudentSession, data: StudentHomeData?, onLogout: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    StudentAvatar(data?.student?.displayName ?: session.displayName)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        data?.student?.displayName?.ifBlank { session.displayName } ?: session.displayName,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MasaryColors.brandNavy,
                    )
                    Text(stringResource(R.string.profile_username, session.username), color = MasaryColors.muted)
                    data?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.profile_total_points, it.summary.globalXp), color = MasaryColors.brandNavy)
                    }
                    Spacer(Modifier.height(18.dp))
                    HorizontalDivider(color = MasaryColors.border)
                    Spacer(Modifier.height(18.dp))
                    OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.logout), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = MasaryColors.iceSurface) {
            Icon(icon, null, tint = MasaryColors.brandNavy, modifier = Modifier.padding(8.dp).size(20.dp))
        }
        Spacer(Modifier.width(9.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = MasaryColors.brandNavy)
    }
}

@Composable
private fun StudentBottomBar(selected: StudentDestination, onSelected: (StudentDestination) -> Unit) {
    NavigationBar(
        modifier = Modifier.navigationBarsPadding(),
        containerColor = MasaryColors.brandNavyDeep,
        tonalElevation = 0.dp,
    ) {
        studentDestinations.forEach { destination ->
            NavigationBarItem(
                selected = destination == selected,
                onClick = { onSelected(destination) },
                icon = { Icon(destination.icon, stringResource(destination.labelRes)) },
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

private fun formatSnapshotAge(savedAtEpochMillis: Long): String {
    val minutes = ((System.currentTimeMillis() - savedAtEpochMillis).coerceAtLeast(0L) / 60_000L).toInt()
    return when {
        minutes < 1 -> "الآن"
        minutes < 60 -> "منذ $minutes دقيقة"
        else -> "منذ ${minutes / 60} ساعة"
    }
}
