package app.masary.feature.home.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Diamond
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.masary.core.models.auth.StudentSession
import app.masary.core.ui.MasaryColors
import app.masary.feature.home.R
import kotlinx.coroutines.launch

private enum class StudentDestination(
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    Home(R.string.nav_home, Icons.Outlined.Home),
    Subjects(R.string.nav_subjects, Icons.Outlined.MenuBook),
    Ranking(R.string.nav_ranking, Icons.Outlined.EmojiEvents),
    Profile(R.string.nav_profile, Icons.Outlined.Person),
}

@Composable
fun StudentHomeRoute(
    session: StudentSession,
    onLogout: () -> Unit,
) {
    var destination by rememberSaveable { mutableStateOf(StudentDestination.Home) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val unavailableMessage = stringResource(R.string.feature_not_connected)

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
                            colors = listOf(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                                MaterialTheme.colorScheme.background,
                                MaterialTheme.colorScheme.background,
                            ),
                        ),
                    ),
            ) {
                when (destination) {
                    StudentDestination.Home -> HomeContent(
                        session = session,
                        onNotifications = {
                            scope.launch { snackbarHostState.showSnackbar(unavailableMessage) }
                        },
                        onGuide = {
                            scope.launch { snackbarHostState.showSnackbar(unavailableMessage) }
                        },
                        onBrowseSubjects = { destination = StudentDestination.Subjects },
                    )

                    StudentDestination.Subjects,
                    StudentDestination.Ranking,
                    -> ComingSoonSection(destination)

                    StudentDestination.Profile -> ProfileSection(
                        session = session,
                        onLogout = onLogout,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeContent(
    session: StudentSession,
    onNotifications: () -> Unit,
    onGuide: () -> Unit,
    onBrowseSubjects: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 18.dp,
            end = 18.dp,
            top = 12.dp,
            bottom = 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            StudentHeader(
                session = session,
                onNotifications = onNotifications,
            )
        }
        item { TodayDecisionCard(onGuide) }
        item {
            SectionTitle(
                title = stringResource(R.string.home_overview),
                icon = Icons.Outlined.AutoAwesome,
            )
        }
        item { OverviewCards() }
        item {
            ContinueJourneyCard(onBrowseSubjects)
        }
        item {
            DailyPlanCard()
        }
    }
}

@Composable
private fun StudentHeader(
    session: StudentSession,
    onNotifications: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StudentAvatar(session.displayName)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.home_greeting, shortDisplayName(session.displayName)),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(R.string.home_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 2.dp,
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
private fun TodayDecisionCard(onGuide: () -> Unit) {
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
                Column {
                    Text(
                        text = stringResource(R.string.home_today_decision),
                        style = MaterialTheme.typography.labelLarge,
                        color = MasaryColors.brandGold,
                    )
                    Text(
                        text = stringResource(R.string.home_today_decision_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.home_today_decision_body),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.82f),
            )
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onGuide,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.home_today_decision_action))
            }
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
private fun OverviewCards() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OverviewCard(
            modifier = Modifier.weight(1f),
            title = stringResource(R.string.home_gems),
            icon = Icons.Outlined.Diamond,
            iconTint = MasaryColors.brandGold,
        )
        OverviewCard(
            modifier = Modifier.weight(1f),
            title = stringResource(R.string.home_streak),
            icon = Icons.Outlined.LocalFireDepartment,
            iconTint = MaterialTheme.colorScheme.primary,
        )
        OverviewCard(
            modifier = Modifier.weight(1f),
            title = stringResource(R.string.home_progress),
            icon = Icons.Outlined.TaskAlt,
            iconTint = MasaryColors.success,
        )
    }
}

@Composable
private fun OverviewCard(
    modifier: Modifier,
    title: String,
    icon: ImageVector,
    iconTint: Color,
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "—",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.home_pending_sync),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun ContinueJourneyCard(onBrowseSubjects: () -> Unit) {
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
                text = stringResource(R.string.home_continue_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.home_continue_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
private fun DailyPlanCard() {
    val steps = listOf(
        R.string.home_plan_start,
        R.string.home_plan_review,
        R.string.home_plan_goal,
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            SectionTitle(
                title = stringResource(R.string.home_plan),
                icon = Icons.Outlined.TaskAlt,
            )
            Spacer(Modifier.height(10.dp))
            steps.forEach { step ->
                Row(
                    modifier = Modifier.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(step),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
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
                StudentAvatar(session.displayName)
                Spacer(Modifier.height(16.dp))
                Text(
                    text = session.displayName,
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.profile_username, session.username),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
