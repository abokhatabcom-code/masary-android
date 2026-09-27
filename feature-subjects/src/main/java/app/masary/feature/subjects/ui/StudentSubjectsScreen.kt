package app.masary.feature.subjects.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.masary.core.ui.MasaryBrandMark
import app.masary.core.ui.MasaryColors
import app.masary.feature.subjects.R
import app.masary.feature.subjects.domain.StudentSubject
import app.masary.feature.subjects.domain.StudentSubjectsData
import app.masary.feature.subjects.domain.SubjectAccessStatus
import app.masary.feature.subjects.domain.SubjectsRepository

@Composable
fun StudentSubjectsRoute(
    repository: SubjectsRepository,
    onSessionExpired: () -> Unit,
    onSubject: (Int) -> Unit,
) {
    val subjectsViewModel: StudentSubjectsViewModel = viewModel(
        factory = StudentSubjectsViewModelFactory(repository),
    )
    val state by subjectsViewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state) {
        if (state == SubjectsUiState.SessionExpired) onSessionExpired()
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        when (val current = state) {
            SubjectsUiState.Loading,
            SubjectsUiState.SessionExpired,
            -> SubjectsLoading()
            is SubjectsUiState.Error -> SubjectsError(current.message, subjectsViewModel::refresh)
            is SubjectsUiState.Content -> SubjectsContent(
                data = current.data,
                isRefreshing = current.isRefreshing,
                refreshMessage = current.refreshMessage,
                onRefresh = subjectsViewModel::refresh,
                onSubject = onSubject,
            )
        }
    }
}

@Composable
private fun SubjectsLoading() {
    Box(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            MasaryBrandMark(size = 72.dp)
            Spacer(Modifier.height(18.dp))
            CircularProgressIndicator(color = MasaryColors.brandGold)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.subjects_loading), color = MasaryColors.muted)
        }
    }
}

@Composable
private fun SubjectsError(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().statusBarsPadding().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                MasaryBrandMark(size = 64.dp)
                Spacer(Modifier.height(14.dp))
                Text(
                    stringResource(R.string.subjects_load_error),
                    style = MaterialTheme.typography.titleLarge,
                    color = MasaryColors.brandNavy,
                )
                Spacer(Modifier.height(8.dp))
                Text(message, color = MasaryColors.muted, textAlign = TextAlign.Center)
                Spacer(Modifier.height(18.dp))
                Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.subjects_retry))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubjectsContent(
    data: StudentSubjectsData,
    isRefreshing: Boolean,
    refreshMessage: String?,
    onRefresh: () -> Unit,
    onSubject: (Int) -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.subjects_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MasaryColors.brandNavy,
                )
                Text(stringResource(R.string.subjects_subtitle), color = MasaryColors.muted)
            }

            val academicLabel = data.academic.displayLabel
            if (academicLabel.isNotBlank()) {
                item { SubjectsBanner(academicLabel) }
            } else if (!data.academic.available) {
                item { SubjectsBanner(stringResource(R.string.subjects_academic_unavailable)) }
            }
            if (data.snapshot != null) {
                item { SubjectsBanner(stringResource(R.string.subjects_cached)) }
            }
            if (!refreshMessage.isNullOrBlank()) {
                item { SubjectsBanner(refreshMessage) }
            }
            if (!data.complete) {
                item { SubjectsBanner(stringResource(R.string.subjects_incomplete)) }
            }

            if (data.subjects.isEmpty()) {
                item {
                    EmptySubjects(
                        data.empty.reason.ifBlank { stringResource(R.string.subjects_empty_title) },
                    )
                }
            } else {
                items(data.subjects, key = StudentSubject::subjectVersionId) { subject ->
                    SubjectCard(subject = subject, onSubject = onSubject)
                }
            }
        }
    }
}

@Composable
private fun SubjectsBanner(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MasaryColors.iceSurface,
        border = BorderStroke(1.dp, MasaryColors.border),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            color = MasaryColors.brandNavy,
        )
    }
}

@Composable
private fun EmptySubjects(reason: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.MenuBook,
                null,
                modifier = Modifier.size(56.dp),
                tint = MasaryColors.brandGold,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.subjects_empty_title),
                style = MaterialTheme.typography.titleLarge,
                color = MasaryColors.brandNavy,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(reason, color = MasaryColors.muted, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun SubjectCard(subject: StudentSubject, onSubject: (Int) -> Unit) {
    val blocked = subject.access.available && subject.access.status == SubjectAccessStatus.Blocked
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !blocked) {
                if (subject.subjectVersionId > 0) onSubject(subject.subjectVersionId)
            },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        border = BorderStroke(1.dp, MasaryColors.border),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MasaryColors.warmSurface) {
                    Icon(
                        Icons.AutoMirrored.Outlined.MenuBook,
                        null,
                        modifier = Modifier.padding(12.dp).size(26.dp),
                        tint = MasaryColors.brandNavy,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        subject.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MasaryColors.brandNavy,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val curriculum = subject.curriculumLabel.takeIf(String::isNotBlank)
                    if (curriculum != null) {
                        Text(curriculum, color = MasaryColors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    val level = subject.level
                    val points = subject.points
                    if (level != null && points != null) {
                        Text(
                            stringResource(R.string.subjects_level_points, level, points),
                            color = MasaryColors.brandNavy,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
                AccessPill(subject.access.status)
            }

            Spacer(Modifier.height(14.dp))
            if (subject.progress.available && subject.progress.percent != null) {
                Text(
                    stringResource(R.string.subjects_progress, subject.progress.percent),
                    style = MaterialTheme.typography.labelMedium,
                    color = MasaryColors.brandNavy,
                )
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { subject.progress.percent.coerceIn(0, 100) / 100f },
                    modifier = Modifier.fillMaxWidth().height(7.dp),
                    color = MasaryColors.brandGold,
                    trackColor = MasaryColors.border,
                )
            } else {
                Text(
                    stringResource(R.string.subjects_progress_unavailable),
                    style = MaterialTheme.typography.labelMedium,
                    color = MasaryColors.muted,
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.FavoriteBorder,
                    null,
                    modifier = Modifier.size(18.dp),
                    tint = MasaryColors.brandGold,
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    stringResource(R.string.subjects_hearts, subject.hearts),
                    style = MaterialTheme.typography.labelMedium,
                    color = MasaryColors.muted,
                )
                if (subject.lastActivity.available) {
                    Spacer(Modifier.width(14.dp))
                    Icon(
                        Icons.Outlined.Schedule,
                        null,
                        modifier = Modifier.size(18.dp),
                        tint = MasaryColors.brandNavy,
                    )
                    Spacer(Modifier.width(5.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.subjects_last_activity),
                            style = MaterialTheme.typography.labelSmall,
                            color = MasaryColors.muted,
                        )
                        val activitySummary = buildList {
                            subject.lastActivity.unitId?.let { add("الوحدة $it") }
                            subject.lastActivity.mode
                                .takeIf(String::isNotBlank)
                                ?.let { add(localizedSubjectActivityMode(it)) }
                        }.joinToString(" • ")
                        if (activitySummary.isNotBlank()) {
                            Text(
                                activitySummary,
                                style = MaterialTheme.typography.labelMedium,
                                color = MasaryColors.brandNavy,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        subject.lastActivity.updatedAt.takeIf(String::isNotBlank)?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelSmall,
                                color = MasaryColors.muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun localizedSubjectActivityMode(mode: String): String = when (mode.trim().lowercase()) {
    "learn", "learning" -> "تعلّم"
    "review" -> "مراجعة"
    "fill" -> "إكمال"
    "connect", "match" -> "توصيل"
    "choose", "mcq" -> "اختيار"
    "truefalse", "true_false" -> "صح أو خطأ"
    "speed" -> "سرعة"
    else -> mode.trim()
}

@Composable
private fun AccessPill(status: SubjectAccessStatus) {
    val (label, color) = when (status) {
        SubjectAccessStatus.Available -> stringResource(R.string.subjects_access_available) to MasaryColors.success
        SubjectAccessStatus.Free -> stringResource(R.string.subjects_access_free) to MasaryColors.success
        SubjectAccessStatus.RequiresSubscription -> stringResource(R.string.subjects_access_subscription) to MasaryColors.brandGold
        SubjectAccessStatus.Blocked -> stringResource(R.string.subjects_access_blocked) to MasaryColors.error
        SubjectAccessStatus.Unknown -> stringResource(R.string.subjects_access_unknown) to MasaryColors.muted
    }
    Surface(shape = CircleShape, color = color.copy(alpha = 0.12f)) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (status == SubjectAccessStatus.Blocked) {
                Icon(Icons.Outlined.Lock, null, modifier = Modifier.size(14.dp), tint = color)
                Spacer(Modifier.width(4.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                maxLines = 1,
            )
        }
    }
}
