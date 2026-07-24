package app.masary.feature.home.ui

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
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Diamond
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
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
import app.masary.feature.activitypreparation.ActivityPreparationDestination
import app.masary.feature.activitypreparation.ActivityPreparationPendingStore
import app.masary.feature.activitypreparation.ActivityPreparationRepository
import app.masary.feature.activitypreparation.ActivityPreparationRoute
import app.masary.feature.home.domain.HomeSmartGuideStep
import app.masary.feature.home.domain.HomeSpotlight
import app.masary.feature.home.domain.HomeSubject
import app.masary.feature.home.domain.StudentHomeData
import app.masary.feature.notifications.NotificationPermissionState

private val StudentDestination.phaseEightIcon: ImageVector
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
    repository: app.masary.feature.home.domain.HomeRepository,
    activityPreparationRepository: ActivityPreparationRepository,
    activityPreparationPendingStore: ActivityPreparationPendingStore,
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
    var showPermissionExplanation by rememberSaveable {
        mutableStateOf(permissionState == NotificationPermissionState.NotRequested)
    }

    val selectedDestination = when {
        backStack?.destination?.hasRoute<ActivityPreparationDestination>() == true ||
            backStack?.destination?.hasRoute<ActivitySessionDestination>() == true -> StudentDestination.Subjects
        backStack?.destination?.hasRoute<StudentDestination.Guide>() == true -> StudentDestination.Guide
        backStack?.destination?.hasRoute<StudentDestination.Subjects>() == true ||
            backStack?.destination?.hasRoute<StudentDestination.SubjectDetails>() == true -> StudentDestination.Subjects
        backStack?.destination?.hasRoute<StudentDestination.Ranking>() == true -> StudentDestination.Ranking
        backStack?.destination?.hasRoute<StudentDestination.Profile>() == true -> StudentDestination.Profile
        else -> StudentDestination.Home
    }

    fun navigateTo(destination: StudentDestination) {
        navController.navigate(destination) {
            launchSingleTop = true
            popUpTo(StudentDestination.Home) { saveState = true }
            restoreState = true
        }
    }

    fun openPreparation(step: HomeSmartGuideStep) {
        navController.navigate(
            ActivityPreparationDestination.fromGuide(
                subjectVersionId = step.subjectVersionId,
                unitId = step.unitId,
                actionKey = step.actionKey,
                guideStepId = step.id.takeIf { it > 0 },
            ),
        ) { launchSingleTop = true }
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
            title = { Text("ابقَ على اطلاع") },
            text = {
                Text("اسمح لمساري بإرسال تذكيرات التعلّم وتنبيهات أمان الحساب. يمكنك استخدام التطبيق كاملًا حتى عند الرفض.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPermissionExplanation = false
                        onNotificationsPermission()
                    },
                ) { Text("متابعة") }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionExplanation = false }) { Text("ليس الآن") }
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
                PhaseEightBottomBar(selectedDestination, ::navigateTo)
            },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = StudentDestination.Home,
                modifier = Modifier.padding(innerPadding),
            ) {
                composable<StudentDestination.Home> {
                    PhaseEightHomeState(
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
                        onGuide = { navigateTo(StudentDestination.Guide) },
                        onGuideStep = ::openPreparation,
                        onSubjects = { navigateTo(StudentDestination.Subjects) },
                        onSubject = { navigateTo(StudentDestination.SubjectDetails(it)) },
                    )
                }
                composable<StudentDestination.Guide> {
                    PhaseEightDataDestination(currentData) { data ->
                        PhaseEightGuide(data.smartGuide.steps, ::openPreparation)
                    }
                }
                composable<StudentDestination.Subjects> {
                    PhaseEightDataDestination(currentData) { data ->
                        PhaseEightSubjects(data.subjects) {
                            navigateTo(StudentDestination.SubjectDetails(it))
                        }
                    }
                }
                composable<StudentDestination.Ranking> {
                    PhaseEightDataDestination(currentData) { data ->
                        PhaseEightSimpleSection(
                            title = "الترتيب",
                            body = data.indicators.globalRank?.let { "ترتيبك العام: $it" }
                                ?: "سيظهر ترتيبك بعد تسجيل نشاط تعليمي.",
                            icon = Icons.Outlined.EmojiEvents,
                        )
                    }
                }
                composable<StudentDestination.Profile> {
                    PhaseEightProfile(session, currentData, onLogout)
                }
                composable<StudentDestination.SubjectDetails> { entry ->
                    val destination = entry.toRoute<StudentDestination.SubjectDetails>()
                    PhaseEightDataDestination(currentData) { data ->
                        val subject = data.subjects.firstOrNull {
                            it.subjectVersionId == destination.subjectVersionId
                        }
                        PhaseEightSimpleSection(
                            title = subject?.name ?: "المادة الدراسية",
                            body = "تُعرض الوحدات والدروس ومركز التدريب في مرحلتها المخصصة. بدء أي نشاط سيبقى محميًا بشاشة التجهيز.",
                            icon = Icons.AutoMirrored.Outlined.MenuBook,
                        )
                    }
                }
                composable<ActivityPreparationDestination> { entry ->
                    ActivityPreparationRoute(
                        destination = entry.toRoute(),
                        repository = activityPreparationRepository,
                        pendingStore = activityPreparationPendingStore,
                        onBack = { navController.popBackStack() },
                        onSessionExpired = onLogout,
                        onStarted = { result ->
                            navController.popBackStack()
                            navController.navigate(
                                ActivitySessionDestination(
                                    sessionId = result.sessionId,
                                    destination = result.destination,
                                    expiresAt = result.expiresAt,
                                ),
                            ) { launchSingleTop = true }
                        },
                    )
                }
                composable<ActivitySessionDestination> { entry ->
                    ActivitySessionReadyScreen(entry.toRoute()) {
                        navController.navigate(StudentDestination.Subjects) {
                            popUpTo(StudentDestination.Home) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhaseEightHomeState(
    session: StudentSession,
    state: HomeUiState,
    onRefresh: () -> Unit,
    onNotifications: () -> Unit,
    onGems: () -> Unit,
    onGuide: () -> Unit,
    onGuideStep: (HomeSmartGuideStep) -> Unit,
    onSubjects: () -> Unit,
    onSubject: (Int) -> Unit,
) {
    when (state) {
        HomeUiState.Loading,
        HomeUiState.SessionExpired,
        -> PhaseEightLoading()
        is HomeUiState.Content -> PhaseEightHome(
            session,
            state.data,
            state.isRefreshing,
            state.refreshMessage,
            onRefresh,
            onNotifications,
            onGems,
            onGuide,
            onGuideStep,
            onSubjects,
            onSubject,
        )
        is HomeUiState.Error -> {
            val previous = state.previousData
            if (previous == null) {
                PhaseEightError(state.message, onRefresh)
            } else {
                PhaseEightHome(
                    session,
                    previous,
                    false,
                    state.message,
                    onRefresh,
                    onNotifications,
                    onGems,
                    onGuide,
                    onGuideStep,
                    onSubjects,
                    onSubject,
                )
            }
        }
    }
}

@Composable
private fun PhaseEightLoading() {
    Box(
        modifier = Modifier.fillMaxSize().background(MasaryColors.brandNavyDeep),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            MasaryBrandMark(size = 84.dp)
            Spacer(Modifier.height(22.dp))
            CircularProgressIndicator(color = MasaryColors.brandGoldBright)
            Spacer(Modifier.height(14.dp))
            Text("نجهّز صفحتك الرئيسية…", color = Color.White)
        }
    }
}

@Composable
private fun PhaseEightError(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().statusBarsPadding().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MasaryBrandMark(size = 70.dp)
                Text("تعذر تحميل الصفحة الرئيسية", fontWeight = FontWeight.Bold)
                Text(message, color = MasaryColors.muted, textAlign = TextAlign.Center)
                Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("إعادة المحاولة") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhaseEightHome(
    session: StudentSession,
    data: StudentHomeData,
    isRefreshing: Boolean,
    refreshMessage: String?,
    onRefresh: () -> Unit,
    onNotifications: () -> Unit,
    onGems: () -> Unit,
    onGuide: () -> Unit,
    onGuideStep: (HomeSmartGuideStep) -> Unit,
    onSubjects: () -> Unit,
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                PhaseEightHeader(
                    gems = data.summary.gems,
                    unread = data.notifications.unreadCount,
                    onGems = onGems,
                    onNotifications = onNotifications,
                )
            }
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        "مرحبًا ${phaseEightDisplayName(data.student.displayName.ifBlank { session.displayName })} 👋",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MasaryColors.brandNavy,
                    )
                    Text("واصل طريقك الواضح نحو التميز", color = MasaryColors.muted)
                    PhaseEightLevelCard(data)
                    PhaseEightIndicators(data)
                    refreshMessage?.takeIf(String::isNotBlank)?.let {
                        Text(it, color = MasaryColors.muted, style = MaterialTheme.typography.bodySmall)
                    }
                    PhaseEightGuideCard(data.smartGuide.nextPendingStep, onGuide, onGuideStep)
                    PhaseEightSubjectPreview(data.subjects, onSubjects, onSubject)
                    data.spotlight?.let { PhaseEightSpotlight(it) }
                }
            }
        }
    }
}

@Composable
private fun PhaseEightHeader(
    gems: Int,
    unread: Int,
    onGems: () -> Unit,
    onNotifications: () -> Unit,
) {
    Surface(color = MasaryColors.brandNavyDeep) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MasaryBrandLockup()
            Spacer(Modifier.weight(1f))
            Surface(
                modifier = Modifier.clickable(onClick = onGems),
                shape = RoundedCornerShape(18.dp),
                color = Color.White.copy(alpha = 0.12f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Diamond, null, tint = MasaryColors.brandGoldBright)
                    Spacer(Modifier.width(6.dp))
                    Text(gems.coerceAtLeast(0).toString(), color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onNotifications) {
                BadgedBox(badge = { if (unread > 0) Badge { Text(unread.coerceAtMost(99).toString()) } }) {
                    Icon(Icons.Outlined.NotificationsNone, "الإشعارات", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun PhaseEightLevelCard(data: StudentHomeData) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("المستوى ${data.summary.level}", fontWeight = FontWeight.Bold, color = MasaryColors.brandNavy)
                    Text("${data.summary.globalXp} نقطة", color = MasaryColors.muted)
                }
                Text("${data.summary.levelPercent.coerceIn(0, 100)}%", color = MasaryColors.brandGold, fontWeight = FontWeight.Bold)
            }
            LinearProgressIndicator(
                progress = { data.summary.levelPercent.coerceIn(0, 100) / 100f },
                modifier = Modifier.fillMaxWidth().height(8.dp),
            )
            Text("الهدف التالي عند ${data.summary.levelNextXp} نقطة", color = MasaryColors.muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PhaseEightIndicators(data: StudentHomeData) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PhaseEightIndicator(Icons.Outlined.Timer, "دقائق اليوم", data.today.minutes.toString(), Modifier.weight(1f))
        PhaseEightIndicator(Icons.Outlined.LocalFireDepartment, "السلسلة", "${data.streak.currentDays} يوم", Modifier.weight(1f))
        PhaseEightIndicator(Icons.Outlined.TaskAlt, "المهام", "غير متاح", Modifier.weight(1f))
        PhaseEightIndicator(Icons.Outlined.Badge, "الشارات", "غير متاح", Modifier.weight(1f))
    }
}

@Composable
private fun PhaseEightIndicator(icon: ImageVector, label: String, value: String, modifier: Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(icon, null, tint = MasaryColors.brandGold)
            Text(value, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(label, color = MasaryColors.muted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

@Composable
private fun PhaseEightGuideCard(
    step: HomeSmartGuideStep?,
    onGuide: () -> Unit,
    onStep: (HomeSmartGuideStep) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MasaryColors.brandNavy)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("الموجّه الدراسي الذكي", color = MasaryColors.brandGoldBright, fontWeight = FontWeight.Bold)
            if (step == null) {
                Text("سيظهر اقتراح الموجّه الدراسي هنا فور تجهيزه من النظام.", color = Color.White)
                OutlinedButton(onClick = onGuide) { Text("فتح الموجّه") }
            } else {
                Text(step.title, color = Color.White, fontWeight = FontWeight.Bold)
                step.subtitle.takeIf(String::isNotBlank)?.let { Text(it, color = Color.White.copy(alpha = 0.8f)) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${step.estimatedMinutes} دقيقة", color = Color.White.copy(alpha = 0.8f))
                    Spacer(Modifier.weight(1f))
                    Button(onClick = { onStep(step) }) { Text(step.ctaLabel.ifBlank { "ابدأ" }) }
                }
            }
        }
    }
}

@Composable
private fun PhaseEightSubjectPreview(
    subjects: List<HomeSubject>,
    onAll: () -> Unit,
    onSubject: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("المواد الدراسية", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = onAll) { Text("عرض الكل") }
        }
        if (subjects.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Text("لا توجد مواد مرتبطة بحسابك حاليًا.", modifier = Modifier.fillMaxWidth().padding(18.dp), color = MasaryColors.muted)
            }
        } else {
            subjects.take(4).forEach { subject ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onSubject(subject.subjectVersionId) },
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(shape = CircleShape, color = MasaryColors.brandGold.copy(alpha = 0.15f)) {
                            Icon(
                                Icons.AutoMirrored.Outlined.MenuBook,
                                null,
                                tint = MasaryColors.brandNavy,
                                modifier = Modifier.padding(10.dp),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(subject.name, fontWeight = FontWeight.Bold, color = MasaryColors.brandNavy)
                            Text("القلوب: ${subject.hearts}", color = MasaryColors.muted)
                        }
                        Text(subject.progressPercent?.let { "$it%" } ?: "—", color = MasaryColors.brandGold, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun PhaseEightSpotlight(spotlight: HomeSpotlight) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(if (spotlight.type == "offer") "عرض" else "خبر", color = MasaryColors.brandGold, fontWeight = FontWeight.Bold)
            Text(spotlight.title, fontWeight = FontWeight.Bold, color = MasaryColors.brandNavy)
            Text(spotlight.body, color = MasaryColors.muted, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun PhaseEightGuide(
    steps: List<HomeSmartGuideStep>,
    onStep: (HomeSmartGuideStep) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("الموجّه الدراسي الذكي", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        if (steps.isEmpty()) {
            item { Text("لا توجد خطوات متاحة الآن.", color = MasaryColors.muted) }
        } else {
            items(steps, key = { it.id }) { step ->
                Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(step.title, fontWeight = FontWeight.Bold, color = MasaryColors.brandNavy)
                        Text(step.subtitle, color = MasaryColors.muted)
                        Button(
                            onClick = { onStep(step) },
                            enabled = step.progressState != "completed",
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (step.progressState == "completed") "اكتملت" else step.ctaLabel.ifBlank { "ابدأ" }) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhaseEightSubjects(subjects: List<HomeSubject>, onSubject: (Int) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("المواد الدراسية", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        items(subjects, key = { it.subjectVersionId }) { subject ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onSubject(subject.subjectVersionId) },
                colors = CardDefaults.cardColors(containerColor = Color.White),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.AutoMirrored.Outlined.MenuBook, null, tint = MasaryColors.brandGold)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(subject.name, fontWeight = FontWeight.Bold)
                        Text("القلوب: ${subject.hearts}", color = MasaryColors.muted)
                    }
                    Text(subject.progressPercent?.let { "$it%" } ?: "—")
                }
            }
        }
    }
}

@Composable
private fun PhaseEightProfile(
    session: StudentSession,
    data: StudentHomeData?,
    onLogout: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("حساب الطالب", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(session.displayName, fontWeight = FontWeight.Bold, color = MasaryColors.brandNavy)
                Text("اسم المستخدم: ${session.username}", color = MasaryColors.muted)
                Text("إجمالي النقاط: ${data?.summary?.globalXp ?: 0}", color = MasaryColors.muted)
            }
        }
        OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) { Text("تسجيل الخروج") }
    }
}

@Composable
private fun PhaseEightSimpleSection(title: String, body: String, icon: ImageVector) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(icon, null, tint = MasaryColors.brandGold, modifier = Modifier.size(56.dp))
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text(body, color = MasaryColors.muted, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun PhaseEightDataDestination(data: StudentHomeData?, content: @Composable (StudentHomeData) -> Unit) {
    if (data == null) PhaseEightLoading() else content(data)
}

@Composable
private fun PhaseEightBottomBar(
    selected: StudentDestination,
    onSelected: (StudentDestination) -> Unit,
) {
    NavigationBar(modifier = Modifier.navigationBarsPadding()) {
        studentDestinations.forEach { destination ->
            NavigationBarItem(
                selected = selected == destination,
                onClick = { onSelected(destination) },
                icon = { Icon(destination.phaseEightIcon, null) },
                label = {
                    Text(
                        when (destination) {
                            StudentDestination.Home -> "الرئيسية"
                            StudentDestination.Guide -> "الموجّه"
                            StudentDestination.Subjects -> "المواد"
                            StudentDestination.Ranking -> "الترتيب"
                            StudentDestination.Profile -> "حسابي"
                            is StudentDestination.SubjectDetails -> "المواد"
                        },
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MasaryColors.brandNavy,
                    selectedTextColor = MasaryColors.brandNavy,
                    indicatorColor = MasaryColors.brandGold.copy(alpha = 0.25f),
                ),
            )
        }
    }
}

private fun phaseEightDisplayName(value: String): String {
    val parts = value.trim().split(Regex("\\s+")).filter(String::isNotBlank)
    return parts.take(2).joinToString(" ").ifBlank { "طالب مساري" }
}
