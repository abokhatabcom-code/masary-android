package app.masary.feature.home.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
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
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.masary.core.models.auth.StudentSession
import app.masary.core.ui.MasaryColors
import app.masary.feature.home.R
import app.masary.feature.home.domain.HomeRepository
import app.masary.feature.home.domain.HomeSmartGuide
import app.masary.feature.home.domain.HomeSmartGuideStep
import app.masary.feature.home.domain.StudentHomeData
import kotlinx.coroutines.launch

private enum class StudentDestination(
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    Home(R.string.nav_home, Icons.Outlined.Home),
    Subjects(R.string.nav_subjects, Icons.AutoMirrored.Outlined.MenuBook),
    Ranking(R.string.nav_ranking, Icons.Outlined.EmojiEvents),
    Profile(R.string.nav_profile, Icons.Outlined.Person),
}

@Composable
fun StudentHomeRoute(
    session: StudentSession,
    repository: HomeRepository,
    onLogout: () -> Unit,
) {
    val homeViewModel: StudentHomeViewModel = viewModel(
        factory = StudentHomeViewModelFactory(repository),
    )
    val state by homeViewModel.state.collectAsStateWithLifecycle()
    var destination by rememberSaveable { mutableStateOf(StudentDestination.Home) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

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
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                StudentBottomBar(
                    selected = destination,
                    onSelected = { destination = it },
                )
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                MaterialTheme.colorScheme.background,
                                MaterialTheme.colorScheme.background,
                            ),
                        ),
                    ),
            ) {
                when (destination) {
                    StudentDestination.Home -> HomeStateContent(
                        session = session,
                        state = state,
                        onRefresh = homeViewModel::refresh,
                        onNotifications = {
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    "سيتم ربط صفحة إشعارات أندرويد الأصلية في مرحلتها الخاصة.",
                                )
                            }
                        },
                        onGuideStep = {
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    "سيتم فتح هذه الخطوة داخل صفحة المادة الأصلية عند ربطها في المرحلة التالية.",
                                )
                            }
                        },
                        onBrowseSubjects = { destination = StudentDestination.Subjects },
                    )

                    StudentDestination.Subjects,
                    StudentDestination.Ranking,
                    -> ComingSoonSection(destination)

                    StudentDestination.Profile -> ProfileSection(
                        session = session,
                        data = currentData,
                        onLogout = onLogout,
                    )
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
    onGuideStep: (HomeSmartGuideStep) -> Unit,
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
            onGuideStep = onGuideStep,
            onBrowseSubjects = onBrowseSubjects,
        )

        is HomeUiState.Error -> {
            if (state.previousData != null) {
                HomeContent(
                    session = session,
                    data = state.previousData,
                    isRefreshing = false,
                    errorMessage = state.message,
                    onRefresh = onRefresh,
                    onNotifications = onNotifications,
                    onGuideStep = onGuideStep,
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
            .statusBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.home_loading),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.home_load_error),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.home_retry))
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
    onGuideStep: (HomeSmartGuideStep) -> Unit,
    onBrowseSubjects: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 18.dp,
            end = 18.dp,
            top = 12.dp,
            bottom = 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            StudentHeader(
                displayName = data.student.displayName.ifBlank { session.displayName },
                unreadCount = data.notifications.unreadCount,
                subscriptionActive = data.subscription.active,
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                onNotifications = onNotifications,
            )
        }
        if (!errorMessage.isNullOrBlank()) {
            item { ErrorBanner(errorMessage, onRefresh) }
        }
        item {
            SmartGuideCard(
                guide = data.smartGuide,
                onGuideStep = onGuideStep,
            )
        }
        item {
            SectionTitle(
                title = stringResource(R.string.home_overview),
                icon = Icons.Outlined.AutoAwesome,
            )
        }
        item { OverviewCards(data) }
        item { TodayActivityCard(data) }
        item { ContinueJourneyCard(data, onBrowseSubjects) }
        item { StreakStatusCard(data) }
    }
}

@Composable
private fun StudentHeader(
    displayName: String,
    unreadCount: Int,
    subscriptionActive: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onNotifications: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StudentAvatar(displayName)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.home_greeting, shortDisplayName(displayName)),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.home_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
            Spacer(Modifier.height(6.dp))
            Surface(
                shape = CircleShape,
                color = if (subscriptionActive) {
                    MasaryColors.success.copy(alpha = 0.14f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            ) {
                Text(
                    text = stringResource(
                        if (subscriptionActive) R.string.home_subscription_active else R.string.home_subscription_inactive,
                    ),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (subscriptionActive) MasaryColors.success else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onRefresh, enabled = !isRefreshing) {
            if (isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = stringResource(R.string.home_refresh),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        BadgedBox(
            badge = {
                if (unreadCount > 0) {
                    Badge {
                        Text(if (unreadCount > 99) "99+" else unreadCount.toString())
                    }
                }
            },
        ) {
            IconButton(onClick = onNotifications) {
                Icon(
                    imageVector = Icons.Outlined.NotificationsNone,
                    contentDescription = stringResource(R.string.home_notifications),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun StudentAvatar(displayName: String) {
    Box(
        modifier = Modifier
            .size(54.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(
                Brush.linearGradient(
                    listOf(MasaryColors.brandPurple, MasaryColors.brandPurpleDark),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = displayName.trim().firstOrNull()?.toString() ?: "م",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
    }
}

@Composable
private fun ErrorBanner(message: String, onRetry: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.home_retry))
            }
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
        colors = CardDefaults.cardColors(containerColor = MasaryColors.brandNavy),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.14f),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = MasaryColors.brandGold,
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
                        color = Color.White,
                    )
                }
                if (guide.totalSteps > 0) {
                    Text(
                        text = "${guide.completionPercent}%",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                }
            }

            if (guide.introText.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = guide.introText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.84f),
                )
            }

            if (guide.totalSteps > 0) {
                Spacer(Modifier.height(14.dp))
                LinearProgressIndicator(
                    progress = { guide.completionPercent / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = MasaryColors.brandGold,
                    trackColor = Color.White.copy(alpha = 0.14f),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(
                        R.string.home_guide_progress,
                        guide.completedSteps,
                        guide.totalSteps,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.76f),
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
                    if (index < guide.steps.lastIndex) {
                        Spacer(Modifier.height(10.dp))
                    }
                }
            } else if (guide.boostNote.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = guide.boostNote,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.82f),
                )
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = Color.White.copy(alpha = if (isNext) 0.14f else 0.09f),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = if (completed) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (completed) MasaryColors.success else MasaryColors.brandGold,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = step.title.ifBlank { step.ctaLabel },
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                    val detail = step.subtitle.ifBlank { step.subjectName }
                    if (detail.isNotBlank()) {
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.78f),
                        )
                    }
                }
                Text(
                    text = stringResource(
                        if (completed) R.string.home_guide_completed else R.string.home_guide_pending,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (completed) MasaryColors.success else MasaryColors.brandGold,
                )
            }

            if (step.reasonText.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = step.reasonText,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.72f),
                )
            }

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (step.estimatedMinutes > 0) {
                    GuideChip(
                        icon = Icons.Outlined.Timer,
                        text = stringResource(R.string.home_guide_minutes, step.estimatedMinutes),
                    )
                }
                if (step.estimatedMinutes > 0 && step.rewardGems > 0) Spacer(Modifier.width(8.dp))
                if (step.rewardGems > 0) {
                    GuideChip(
                        icon = Icons.Outlined.Diamond,
                        text = stringResource(R.string.home_guide_reward, step.rewardGems),
                    )
                }
                Spacer(Modifier.weight(1f))
                if (isNext && !completed) {
                    Button(onClick = onClick) {
                        Text(step.ctaLabel.ifBlank { "ابدأ الآن" })
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideChip(icon: ImageVector, text: String) {
    Surface(
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.12f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.84f),
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.84f),
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun OverviewCards(data: StudentHomeData) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MetricCard(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.home_gems),
                value = data.summary.gems.toString(),
                caption = stringResource(R.string.home_points, data.summary.globalXp),
                icon = Icons.Outlined.Diamond,
                iconTint = MasaryColors.brandGold,
            )
            MetricCard(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.home_streak),
                value = data.streak.currentDays.toString(),
                caption = stringResource(R.string.home_days, data.streak.currentDays),
                icon = Icons.Outlined.LocalFireDepartment,
                iconTint = MaterialTheme.colorScheme.primary,
            )
        }
        LevelCard(data)
    }
}

@Composable
private fun MetricCard(
    modifier: Modifier,
    title: String,
    value: String,
    caption: String,
    icon: ImageVector,
    iconTint: Color,
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LevelCard(data: StudentHomeData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.School,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.home_level_value, data.summary.level),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${data.summary.levelPercent}%",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { data.summary.levelPercent / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.home_points, data.summary.globalXp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TodayActivityCard(data: StudentHomeData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            SectionTitle(
                title = stringResource(R.string.home_today_activity),
                icon = Icons.Outlined.TaskAlt,
            )
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                TodayMetric(data.today.minutes.toString(), stringResource(R.string.home_minutes))
                TodayMetric(data.today.attempts.toString(), stringResource(R.string.home_attempts))
                TodayMetric(data.today.xp.toString(), stringResource(R.string.home_xp))
            }
        }
    }
}

@Composable
private fun TodayMetric(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            SectionTitle(
                title = stringResource(R.string.home_continue),
                icon = Icons.Outlined.School,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = item.label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = item.hint.ifBlank { stringResource(R.string.home_no_last_activity) },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (item.hearts != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.home_hearts, item.hearts),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (item.disabledReason.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = item.disabledReason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(
                onClick = onBrowseSubjects,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.home_browse_subjects))
            }
        }
    }
}

@Composable
private fun StreakStatusCard(data: StudentHomeData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            SectionTitle(
                title = stringResource(R.string.home_streak_status),
                icon = Icons.Outlined.LocalFireDepartment,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = data.streak.message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.home_streak_best, data.streak.bestDays),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(
                        R.string.home_streak_shields,
                        data.streak.protectionCount,
                        data.streak.protectionMax,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (data.streak.goal.days > 0) {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = stringResource(R.string.home_streak_goal, data.streak.goal.days),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { data.streak.goal.progressPercent / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(
                        R.string.home_streak_goal_remaining,
                        data.streak.goal.remainingDays,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ComingSoonSection(destination: StudentDestination) {
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
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = destination.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp),
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    text = stringResource(
                        R.string.coming_soon_title,
                        stringResource(destination.labelRes),
                    ),
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.coming_soon_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                StudentAvatar(data?.student?.displayName?.ifBlank { session.displayName } ?: session.displayName)
                Spacer(Modifier.height(16.dp))
                Text(
                    text = data?.student?.displayName?.ifBlank { session.displayName } ?: session.displayName,
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.profile_username, session.username),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (data != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.profile_total_points, data.summary.globalXp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(
                            if (data.subscription.active) R.string.home_subscription_active else R.string.home_subscription_inactive,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (data.subscription.active) MasaryColors.success else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (data.subscription.endsAt.isNotBlank()) {
                        Text(
                            text = stringResource(R.string.home_subscription_until, data.subscription.endsAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        text = stringResource(R.string.profile_session_secure),
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(22.dp))
                OutlinedButton(
                    onClick = onLogout,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.logout))
                }
            }
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
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
    ) {
        StudentDestination.entries.forEach { destination ->
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
            )
        }
    }
}

internal fun shortDisplayName(displayName: String): String {
    val names = displayName.trim().split(Regex("\\s+")).filter(String::isNotBlank)
    return names.take(2).joinToString(" ").ifBlank { "طالب مساري" }
}
