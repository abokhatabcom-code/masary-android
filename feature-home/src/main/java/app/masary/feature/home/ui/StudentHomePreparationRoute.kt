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
import androidx.compose.material.icons.outlined.Diamond
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import app.masary.core.ui.MasaryBrandMark
import app.masary.core.ui.MasaryColors
import app.masary.feature.activitypreparation.ActivityPreparationDestination
import app.masary.feature.activitypreparation.ActivityPreparationPendingStore
import app.masary.feature.activitypreparation.ActivityPreparationRepository
import app.masary.feature.activitypreparation.ActivityPreparationRoute
import app.masary.feature.home.domain.HomeSmartGuideStep
import app.masary.feature.home.domain.HomeSubject
import app.masary.feature.home.domain.StudentHomeData
import app.masary.feature.notifications.NotificationPermissionState
import app.masary.feature.subject.domain.SubjectRepository
import app.masary.feature.subject.ui.StudentSubjectRoute
import app.masary.feature.subjects.domain.SubjectsRepository
import app.masary.feature.subjects.ui.StudentSubjectsRoute

private val StudentDestination.preparationIcon: ImageVector
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
    subjectsRepository: SubjectsRepository,
    subjectRepository: SubjectRepository,
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
    var explainNotifications by rememberSaveable {
        mutableStateOf(permissionState == NotificationPermissionState.NotRequested)
    }

    val selected = when {
        backStack?.destination?.hasRoute<ActivityPreparationDestination>() == true ||
            backStack?.destination?.hasRoute<ActivitySessionDestination>() == true -> StudentDestination.Subjects
        backStack?.destination?.hasRoute<StudentDestination.Guide>() == true -> StudentDestination.Guide
        backStack?.destination?.hasRoute<StudentDestination.Subjects>() == true ||
            backStack?.destination?.hasRoute<StudentDestination.SubjectDetails>() == true -> StudentDestination.Subjects
        backStack?.destination?.hasRoute<StudentDestination.Ranking>() == true -> StudentDestination.Ranking
        backStack?.destination?.hasRoute<StudentDestination.Profile>() == true -> StudentDestination.Profile
        else -> StudentDestination.Home
    }

    fun navigate(destination: StudentDestination) {
        navController.navigate(destination) {
            launchSingleTop = true
            popUpTo(StudentDestination.Home) { saveState = true }
            restoreState = true
        }
    }

    fun prepare(step: HomeSmartGuideStep) {
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
            navigate(externalStudentDestination(it))
            onExternalDestinationConsumed()
        }
    }
    LaunchedEffect(state) {
        if (state == HomeUiState.SessionExpired) onLogout()
    }

    if (explainNotifications) {
        AlertDialog(
            onDismissRequest = { explainNotifications = false },
            title = { Text("ابقَ على اطلاع") },
            text = { Text("اسمح لمساري بإرسال تذكيرات التعلّم وتنبيهات أمان الحساب.") },
            confirmButton = {
                TextButton(onClick = {
                    explainNotifications = false
                    onNotificationsPermission()
                }) { Text("متابعة") }
            },
            dismissButton = {
                TextButton(onClick = { explainNotifications = false }) { Text("ليس الآن") }
            },
        )
    }

    val data = when (val current = state) {
        is HomeUiState.Content -> current.data
        is HomeUiState.Error -> current.previousData
        HomeUiState.Loading,
        HomeUiState.SessionExpired,
        -> null
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            containerColor = MasaryColors.background,
            bottomBar = { PreparationBottomBar(selected, ::navigate) },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = StudentDestination.Home,
                modifier = Modifier.padding(padding),
            ) {
                composable<StudentDestination.Home> {
                    PreparationHomeState(
                        session = session,
                        state = state,
                        onRefresh = homeViewModel::refresh,
                        onNotification = {
                            when (permissionState) {
                                NotificationPermissionState.NotRequested,
                                NotificationPermissionState.Denied,
                                -> explainNotifications = true
                                NotificationPermissionState.PermanentlyDenied,
                                NotificationPermissionState.SystemDisabled,
                                -> onOpenNotificationSettings()
                                else -> Unit
                            }
                        },
                        onGuide = { navigate(StudentDestination.Guide) },
                        onStep = ::prepare,
                        onSubjects = { navigate(StudentDestination.Subjects) },
                        onSubject = { navigate(StudentDestination.SubjectDetails(it)) },
                        onProfile = { navigate(StudentDestination.Profile) },
                    )
                }
                composable<StudentDestination.Guide> {
                    PreparationDataDestination(data) { snapshot ->
                        GuideList(snapshot.smartGuide.steps, ::prepare)
                    }
                }
                composable<StudentDestination.Subjects> {
                    StudentSubjectsRoute(
                        repository = subjectsRepository,
                        onSubject = { subjectVersionId ->
                            if (subjectVersionId > 0) {
                                navigate(StudentDestination.SubjectDetails(subjectVersionId))
                            }
                        },
                        onSessionExpired = onLogout,
                    )
                }
                composable<StudentDestination.Ranking> {
                    PreparationDataDestination(data) { snapshot ->
                        SimpleSection(
                            title = "الترتيب",
                            body = snapshot.indicators.globalRank?.let { "ترتيبك العام: $it" }
                                ?: "سيظهر ترتيبك بعد تسجيل نشاط تعليمي.",
                            icon = Icons.Outlined.EmojiEvents,
                        )
                    }
                }
                composable<StudentDestination.Profile> {
                    ProfileSection(session, data, onLogout)
                }
                composable<StudentDestination.SubjectDetails> { entry ->
            val destination = entry.toRoute<StudentDestination.SubjectDetails>()
            StudentSubjectRoute(
                subjectVersionId = destination.subjectVersionId,
                repository = subjectRepository,
                onBack = { navController.popBackStack() },
                onSessionExpired = onLogout,
                onTrainingCenter = { /* Phase 11 owns this destination. */ },
            )
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
private fun PreparationHomeState(
    session: StudentSession,
    state: HomeUiState,
    onRefresh: () -> Unit,
    onNotification: () -> Unit,
    onGuide: () -> Unit,
    onStep: (HomeSmartGuideStep) -> Unit,
    onSubjects: () -> Unit,
    onSubject: (Int) -> Unit,
    onProfile: () -> Unit,
) {
    when (state) {
        HomeUiState.Loading,
        HomeUiState.SessionExpired,
        -> PreparationLoading()
        is HomeUiState.Content -> HomeContent(
            session = session,
            data = state.data,
            refreshing = state.isRefreshing,
            message = state.refreshMessage,
            onRefresh = onRefresh,
            onNotification = onNotification,
            onGuide = onGuide,
            onStep = onStep,
            onSubjects = onSubjects,
            onSubject = onSubject,
            onProfile = onProfile,
        )
        is HomeUiState.Error -> {
            val previous = state.previousData
            if (previous == null) {
                PreparationError(state.message, onRefresh)
            } else {
                HomeContent(
                    session = session,
                    data = previous,
                    refreshing = false,
                    message = state.message,
                    onRefresh = onRefresh,
                    onNotification = onNotification,
                    onGuide = onGuide,
                    onStep = onStep,
                    onSubjects = onSubjects,
                    onSubject = onSubject,
                    onProfile = onProfile,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeContent(
    session: StudentSession,
    data: StudentHomeData,
    refreshing: Boolean,
    message: String?,
    onRefresh: () -> Unit,
    onNotification: () -> Unit,
    onGuide: () -> Unit,
    onStep: (HomeSmartGuideStep) -> Unit,
    onSubjects: () -> Unit,
    onSubject: (Int) -> Unit,
    onProfile: () -> Unit,
) {
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                HomeHeader(
                    gems = data.summary.gems,
                    unread = data.notifications.unreadCount,
                    onGems = onProfile,
                    onNotification = onNotification,
                )
            }
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        "مرحبًا ${displayName(data.student.displayName.ifBlank { session.displayName })} 👋",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MasaryColors.brandNavy,
                    )
                    LevelCard(data)
                    IndicatorRow(data)
                    message?.takeIf(String::isNotBlank)?.let {
                        Text(it, color = MasaryColors.muted, style = MaterialTheme.typography.bodySmall)
                    }
                    GuideCard(data.smartGuide.nextPendingStep, onGuide, onStep)
                    SubjectPreview(data.subjects, onSubjects, onSubject)
                    data.spotlight?.let { spotlight ->
                        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(if (spotlight.type == "offer") "عرض" else "خبر", color = MasaryColors.brandGold)
                                Text(spotlight.title, fontWeight = FontWeight.Bold)
                                Text(
                                    spotlight.body,
                                    color = MasaryColors.muted,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(
    gems: Int,
    unread: Int,
    onGems: () -> Unit,
    onNotification: () -> Unit,
) {
    Surface(color = MasaryColors.brandNavyDeep) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("مساري", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Surface(
                modifier = Modifier.clickable(onClick = onGems),
                color = Color.White.copy(alpha = 0.12f),
                shape = RoundedCornerShape(18.dp),
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
            IconButton(onClick = onNotification) {
                Box {
                    Icon(Icons.Outlined.NotificationsNone, "الإشعارات", tint = Color.White)
                    if (unread > 0) {
                        Surface(
                            modifier = Modifier.align(Alignment.TopEnd).size(16.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.error,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    unread.coerceAtMost(9).toString(),
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LevelCard(data: StudentHomeData) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("المستوى ${data.summary.level}", fontWeight = FontWeight.Bold)
                    Text("${data.summary.globalXp} نقطة", color = MasaryColors.muted)
                }
                Text("${data.summary.levelPercent.coerceIn(0, 100)}%", color = MasaryColors.brandGold)
            }
            LinearProgressIndicator(
                progress = { data.summary.levelPercent.coerceIn(0, 100) / 100f },
                modifier = Modifier.fillMaxWidth().height(8.dp),
            )
        }
    }
}

@Composable
private fun IndicatorRow(data: StudentHomeData) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Indicator(Icons.Outlined.Timer, "دقائق", data.today.minutes.toString(), Modifier.weight(1f))
        Indicator(Icons.Outlined.LocalFireDepartment, "السلسلة", data.streak.currentDays.toString(), Modifier.weight(1f))
        Indicator(Icons.Outlined.TaskAlt, "المهام", "—", Modifier.weight(1f))
        Indicator(Icons.Outlined.EmojiEvents, "الشارات", "—", Modifier.weight(1f))
    }
}

@Composable
private fun Indicator(icon: ImageVector, label: String, value: String, modifier: Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(icon, null, tint = MasaryColors.brandGold)
            Text(value, fontWeight = FontWeight.Bold)
            Text(label, color = MasaryColors.muted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun GuideCard(
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
                Text("لا توجد خطوة معلقة الآن.", color = Color.White)
                OutlinedButton(onClick = onGuide) { Text("فتح الموجّه") }
            } else {
                Text(step.title, color = Color.White, fontWeight = FontWeight.Bold)
                Text(step.subtitle, color = Color.White.copy(alpha = 0.8f))
                Button(onClick = { onStep(step) }, modifier = Modifier.fillMaxWidth()) {
                    Text(step.ctaLabel.ifBlank { "ابدأ" })
                }
            }
        }
    }
}

@Composable
private fun SubjectPreview(
    subjects: List<HomeSubject>,
    onAll: () -> Unit,
    onSubject: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("المواد الدراسية", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = onAll) { Text("عرض الكل") }
        }
        subjects.take(4).forEach { subject ->
            SubjectCard(subject) { onSubject(subject.subjectVersionId) }
        }
        if (subjects.isEmpty()) Text("لا توجد مواد مرتبطة بالحساب.", color = MasaryColors.muted)
    }
}

@Composable
private fun SubjectList(subjects: List<HomeSubject>, onSubject: (Int) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Text("المواد الدراسية", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        items(subjects, key = { it.subjectVersionId }) { subject ->
            SubjectCard(subject) { onSubject(subject.subjectVersionId) }
        }
    }
}

@Composable
private fun SubjectCard(subject: HomeSubject, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
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

@Composable
private fun GuideList(steps: List<HomeSmartGuideStep>, onStep: (HomeSmartGuideStep) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Text("الموجّه الدراسي الذكي", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        items(steps, key = { it.id }) { step ->
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(step.title, fontWeight = FontWeight.Bold)
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

@Composable
private fun ProfileSection(session: StudentSession, data: StudentHomeData?, onLogout: () -> Unit) {
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
                Text(session.displayName, fontWeight = FontWeight.Bold)
                Text("اسم المستخدم: ${session.username}", color = MasaryColors.muted)
                Text("إجمالي النقاط: ${data?.summary?.globalXp ?: 0}", color = MasaryColors.muted)
            }
        }
        OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) { Text("تسجيل الخروج") }
    }
}

@Composable
private fun SimpleSection(title: String, body: String, icon: ImageVector) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(icon, null, tint = MasaryColors.brandGold, modifier = Modifier.size(54.dp))
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(body, color = MasaryColors.muted, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun PreparationDataDestination(data: StudentHomeData?, content: @Composable (StudentHomeData) -> Unit) {
    if (data == null) PreparationLoading() else content(data)
}

@Composable
private fun PreparationLoading() {
    Box(
        modifier = Modifier.fillMaxSize().background(MasaryColors.brandNavyDeep),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            MasaryBrandMark(size = 80.dp)
            Spacer(Modifier.height(20.dp))
            CircularProgressIndicator(color = MasaryColors.brandGoldBright)
        }
    }
}

@Composable
private fun PreparationError(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("تعذر تحميل الصفحة", fontWeight = FontWeight.Bold)
                Text(message, color = MasaryColors.muted, textAlign = TextAlign.Center)
                Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("إعادة المحاولة") }
            }
        }
    }
}

@Composable
private fun PreparationBottomBar(selected: StudentDestination, onSelected: (StudentDestination) -> Unit) {
    NavigationBar(modifier = Modifier.navigationBarsPadding()) {
        studentDestinations.forEach { destination ->
            NavigationBarItem(
                selected = selected == destination,
                onClick = { onSelected(destination) },
                icon = { Icon(destination.preparationIcon, null) },
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
                    indicatorColor = MasaryColors.brandGold.copy(alpha = 0.22f),
                ),
            )
        }
    }
}

private fun displayName(value: String): String {
    val parts = value.trim().split(Regex("\\s+")).filter(String::isNotBlank)
    return parts.take(2).joinToString(" ").ifBlank { "طالب مساري" }
}