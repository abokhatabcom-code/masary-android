package app.masary.feature.subject.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import app.masary.feature.subject.R
import app.masary.feature.subject.domain.StudentSubjectPage
import app.masary.feature.subject.domain.SubjectContentPart
import app.masary.feature.subject.domain.SubjectRepository
import app.masary.feature.subject.domain.SubjectStructureMode

@Composable
fun StudentSubjectRoute(
    subjectVersionId: Int,
    repository: SubjectRepository,
    onBack: () -> Unit,
    onSessionExpired: () -> Unit,
    onTrainingCenter: (Int) -> Unit,
) {
    val subjectViewModel: StudentSubjectViewModel = viewModel(
        key = "subject-$subjectVersionId",
        factory = StudentSubjectViewModelFactory(subjectVersionId, repository),
    )
    val state by subjectViewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state) {
        if (state == SubjectUiState.SessionExpired) onSessionExpired()
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        when (val current = state) {
            SubjectUiState.Loading,
            SubjectUiState.SessionExpired,
            -> SubjectLoading(onBack)
            is SubjectUiState.NotFound -> SubjectMessage(
                title = stringResource(R.string.subject_not_found),
                message = current.message,
                onBack = onBack,
                onRetry = null,
            )
            is SubjectUiState.Error -> SubjectMessage(
                title = stringResource(R.string.subject_load_error),
                message = current.message,
                onBack = onBack,
                onRetry = subjectViewModel::refresh,
            )
            is SubjectUiState.Content -> SubjectContent(
                data = current.data,
                isRefreshing = current.isRefreshing,
                refreshMessage = current.refreshMessage,
                onRefresh = subjectViewModel::refresh,
                onBack = onBack,
                onTrainingCenter = onTrainingCenter,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubjectContent(
    data: StudentSubjectPage,
    isRefreshing: Boolean,
    refreshMessage: String?,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    onTrainingCenter: (Int) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MasaryColors.background,
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(
                            data.identity.name,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Bold,
                            color = MasaryColors.brandNavy,
                        )
                        data.identity.curriculumLabel.takeIf(String::isNotBlank)?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MasaryColors.muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.subject_back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item { SubjectIdentityCard(data) }
                data.snapshot?.let {
                    item { SubjectBanner(stringResource(R.string.subject_cached)) }
                }
                refreshMessage?.takeIf(String::isNotBlank)?.let {
                    item { SubjectBanner(it) }
                }
                item { SubjectProgressCard(data) }
                item { SubjectHeartsCard(data) }
                item {
                    SubjectTrainingCard(
                        data = data,
                        onTrainingCenter = onTrainingCenter,
                    )
                }
                item { SubjectContentHeader(data) }
                if (data.content.parts.isEmpty()) {
                    item { SubjectContentPlaceholder(data.content.reason) }
                } else {
                    items(data.content.parts, key = SubjectContentPart::partNumber) { part ->
                        SubjectPartCard(part)
                    }
                    item { SubjectContentPlaceholder(data.content.reason) }
                }
                item { SubjectLastActivityCard(data) }
            }
        }
    }
}

@Composable
private fun SubjectIdentityCard(data: StudentSubjectPage) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MasaryColors.brandNavy),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = Color.White.copy(alpha = 0.13f),
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.MenuBook,
                    null,
                    modifier = Modifier.padding(16.dp).size(42.dp),
                    tint = MasaryColors.brandGoldBright,
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    data.identity.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                val label = data.identity.curriculumLabel.ifBlank { data.identity.versionType }
                if (label.isNotBlank()) {
                    Text(label, color = Color.White.copy(alpha = 0.78f))
                }
                Text(
                    when (data.content.structureMode) {
                        SubjectStructureMode.Units -> stringResource(R.string.subject_units)
                        SubjectStructureMode.Lessons -> stringResource(R.string.subject_lessons)
                        SubjectStructureMode.Unknown -> stringResource(R.string.subject_unavailable)
                    },
                    color = MasaryColors.brandGoldBright,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun SubjectProgressCard(data: StudentSubjectPage) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.subject_progress),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MasaryColors.brandNavy,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Metric(
                    label = stringResource(R.string.subject_points),
                    value = data.points.value?.toString().takeIf { data.points.available }
                        ?: stringResource(R.string.subject_unavailable),
                    modifier = Modifier.weight(1f),
                )
                Metric(
                    label = stringResource(R.string.subject_level),
                    value = data.level.value?.toString().takeIf { data.level.available }
                        ?: stringResource(R.string.subject_unavailable),
                    modifier = Modifier.weight(1f),
                )
            }
            if (data.progress.available && data.progress.percent != null) {
                LinearProgressIndicator(
                    progress = { data.progress.percent.coerceIn(0, 100) / 100f },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = MasaryColors.brandGold,
                    trackColor = MasaryColors.border,
                )
                Text("${data.progress.percent}%", color = MasaryColors.muted)
            } else {
                Text(data.progress.reason, color = MasaryColors.muted)
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MasaryColors.iceSurface,
        border = BorderStroke(1.dp, MasaryColors.border),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, fontWeight = FontWeight.ExtraBold, color = MasaryColors.brandNavy)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MasaryColors.muted)
        }
    }
}

@Composable
private fun SubjectHeartsCard(data: StudentSubjectPage) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(R.string.subject_hearts),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MasaryColors.brandNavy,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(data.hearts.maximum.coerceAtMost(6)) { index ->
                    Icon(
                        if (index < data.hearts.current) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                        null,
                        tint = if (index < data.hearts.current) MasaryColors.error else MasaryColors.muted,
                        modifier = Modifier.size(30.dp),
                    )
                }
            }
            Text(
                if (data.hearts.nextRestore.available && !data.hearts.nextRestore.at.isNullOrBlank()) {
                    data.hearts.nextRestore.at
                } else {
                    data.hearts.nextRestore.reason.ifBlank { stringResource(R.string.subject_restore_unknown) }
                },
                color = MasaryColors.muted,
            )
        }
    }
}

@Composable
private fun SubjectTrainingCard(data: StudentSubjectPage, onTrainingCenter: (Int) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MasaryColors.warmSurface)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.School, null, tint = MasaryColors.brandNavy)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.subject_training_center),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MasaryColors.brandNavy,
                )
            }
            Button(
                onClick = { onTrainingCenter(data.subjectVersionId) },
                enabled = data.actions.trainingCenter.available,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.subject_training_center)) }
            if (!data.actions.trainingCenter.available) {
                Text(data.actions.trainingCenter.reason, color = MasaryColors.muted)
            }
        }
    }
}

@Composable
private fun SubjectContentHeader(data: StudentSubjectPage) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            stringResource(R.string.subject_content),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = MasaryColors.brandNavy,
        )
        if (data.content.hasParts && data.content.parts.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                data.content.parts.take(3).forEach { part ->
                    AssistChip(onClick = {}, label = { Text(part.label) })
                }
            }
        }
    }
}

@Composable
private fun SubjectPartCard(part: SubjectContentPart) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Outlined.MenuBook, null, tint = MasaryColors.brandGold)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(part.label, fontWeight = FontWeight.Bold, color = MasaryColors.brandNavy)
                Text(
                    stringResource(R.string.subject_part_counts, part.unitsCount, part.lessonsCount),
                    color = MasaryColors.muted,
                )
            }
        }
    }
}

@Composable
private fun SubjectContentPlaceholder(reason: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MasaryColors.iceSurface,
        border = BorderStroke(1.dp, MasaryColors.border),
    ) {
        Text(
            reason,
            modifier = Modifier.padding(16.dp),
            color = MasaryColors.muted,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SubjectLastActivityCard(data: StudentSubjectPage) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.History, null, tint = MasaryColors.brandGold)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.subject_last_activity),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MasaryColors.brandNavy,
                )
            }
            if (data.lastActivity.available) {
                data.lastActivity.unitId?.let {
                    Text(stringResource(R.string.subject_last_activity_unit, it), color = MasaryColors.brandNavy)
                }
                data.lastActivity.mode.takeIf(String::isNotBlank)?.let {
                    Text(stringResource(R.string.subject_last_activity_mode, it), color = MasaryColors.muted)
                }
                data.lastActivity.updatedAt.takeIf(String::isNotBlank)?.let {
                    Text(it, color = MasaryColors.muted, style = MaterialTheme.typography.bodySmall)
                }
                if (!data.lastActivity.preparation.available) {
                    Text(data.lastActivity.preparation.reason, color = MasaryColors.muted)
                }
            } else {
                Text(
                    data.lastActivity.reason.ifBlank { stringResource(R.string.subject_no_last_activity) },
                    color = MasaryColors.muted,
                )
            }
        }
    }
}

@Composable
private fun SubjectBanner(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MasaryColors.iceSurface,
        border = BorderStroke(1.dp, MasaryColors.border),
    ) {
        Text(message, modifier = Modifier.padding(12.dp), color = MasaryColors.brandNavy)
    }
}

@Composable
private fun SubjectLoading(onBack: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(12.dp)) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.subject_back))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            MasaryBrandMark(size = 72.dp)
            Spacer(Modifier.height(16.dp))
            CircularProgressIndicator(color = MasaryColors.brandGold)
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.subject_loading), color = MasaryColors.muted)
        }
    }
}

@Composable
private fun SubjectMessage(
    title: String,
    message: String,
    onBack: () -> Unit,
    onRetry: (() -> Unit)?,
) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MasaryBrandMark(size = 64.dp)
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(message, color = MasaryColors.muted, textAlign = TextAlign.Center)
                onRetry?.let {
                    Button(onClick = it, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.subject_retry))
                    }
                }
                OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.subject_back))
                }
            }
        }
    }
}
