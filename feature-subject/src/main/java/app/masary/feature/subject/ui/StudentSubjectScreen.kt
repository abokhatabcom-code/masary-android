package app.masary.feature.subject.ui

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.School
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.rotate
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
import app.masary.feature.subject.domain.SubjectUnit
import app.masary.feature.subject.domain.SubjectLesson
import app.masary.feature.subject.domain.SubjectLearningStatus
import app.masary.feature.subject.domain.SubjectRepository
import app.masary.feature.subject.domain.SubjectStructureMode
import java.text.DateFormat
import java.util.Date

@Composable
fun StudentSubjectRoute(
    subjectVersionId: Int,
    repository: SubjectRepository,
    onBack: () -> Unit,
    onSessionExpired: () -> Unit,
    onTrainingCenter: (Int) -> Unit,
    onLessonPreparation: (subjectVersionId: Int, unitId: Int, lessonId: Int) -> Unit,
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
                onLessonPreparation = onLessonPreparation,
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
    onLessonPreparation: (subjectVersionId: Int, unitId: Int, lessonId: Int) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var expandedUnitIds by remember(data.subjectVersionId) { mutableStateOf(emptySet<Int>()) }
    var expansionInitialized by remember(data.subjectVersionId) { mutableStateOf(false) }

    LaunchedEffect(data.content.units, data.lastActivity.unitId) {
        if (!expansionInitialized && data.content.units.isNotEmpty()) {
            val preferredUnitId = preferredExpandedUnitId(
                units = data.content.units,
                lastActivityUnitId = data.lastActivity.unitId,
            )
            expandedUnitIds = preferredUnitId?.let(::setOf).orEmpty()
            expansionInitialized = true
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MasaryColors.background,
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(
                            text = data.identity.name,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Bold,
                            color = MasaryColors.brandNavy,
                        )
                        data.identity.curriculumLabel.takeIf(String::isNotBlank)?.let { curriculum ->
                            Text(
                                text = curriculum,
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
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.subject_back),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item { SubjectIdentityCard(data) }
                data.snapshot?.let { snapshot ->
                    item {
                        SubjectBanner(
                            stringResource(
                                R.string.subject_cached_at,
                                formatSnapshotTime(snapshot.savedAtEpochMillis),
                            ),
                        )
                    }
                }
                refreshMessage?.takeIf(String::isNotBlank)?.let { message ->
                    item { SubjectBanner(message) }
                }
                item { SubjectProgressCard(data) }
                item { SubjectHeartsCard(data) }
                item { SubjectTrainingCard(data, onTrainingCenter) }
                item { SubjectContentHeader(data) }
                if (data.content.detailsAvailable) {
                    if (data.content.units.isEmpty() && data.content.lessons.isEmpty()) {
                        item { SubjectContentPlaceholder(data.content.reason) }
                    } else {
                        items(data.content.units, key = SubjectUnit::id) { unit ->
                            SubjectUnitCard(
                                unit = unit,
                                partLabel = data.content.parts
                                    .firstOrNull { it.partNumber == unit.partNumber }
                                    ?.label
                                    .orEmpty(),
                                expanded = unit.id in expandedUnitIds,
                                onToggle = {
                                    expandedUnitIds = if (unit.id in expandedUnitIds) {
                                        expandedUnitIds - unit.id
                                    } else {
                                        expandedUnitIds + unit.id
                                    }
                                },
                                onLesson = { lesson ->
                                    val lessonUnitId = lesson.unitId
                                    if (lesson.preparation.available && lessonUnitId != null) {
                                        onLessonPreparation(data.subjectVersionId, lessonUnitId, lesson.id)
                                    }
                                },
                            )
                        }
                        if (data.content.lessons.isNotEmpty()) {
                            item {
                                SectionTitle(stringResource(R.string.subject_standalone_lessons))
                            }
                            items(data.content.lessons, key = SubjectLesson::id) { lesson ->
                                SubjectStandaloneLessonCard(
                                    lesson = lesson,
                                    partLabel = data.content.parts
                                        .firstOrNull { it.partNumber == lesson.partNumber }
                                        ?.label
                                        .orEmpty(),
                                    onLesson = {
                                        val lessonUnitId = lesson.unitId
                                        if (lesson.preparation.available && lessonUnitId != null) {
                                            onLessonPreparation(data.subjectVersionId, lessonUnitId, lesson.id)
                                        }
                                    },
                                )
                            }
                        }
                    }
                } else if (data.content.parts.isEmpty()) {
                    item { SubjectContentPlaceholder(data.content.reason) }
                } else {
                    items(data.content.parts, key = SubjectContentPart::partNumber) { part ->
                        SubjectPartCard(part)
                    }
                    item { SubjectContentPlaceholder(data.content.reason) }
                }
                item {
                    SubjectLastActivityCard(
                        data = data,
                        onContinue = {
                            val unitId = data.lastActivity.unitId
                            val lessonId = data.lastActivity.lessonId
                            if (
                                data.lastActivity.preparation.available &&
                                unitId != null &&
                                lessonId != null
                            ) {
                                onLessonPreparation(data.subjectVersionId, unitId, lessonId)
                            }
                        },
                    )
                }
            }
        }
    }
}

internal fun preferredExpandedUnitId(
    units: List<SubjectUnit>,
    lastActivityUnitId: Int?,
): Int? {
    if (units.isEmpty()) return null
    lastActivityUnitId
        ?.takeIf { activeId -> units.any { it.id == activeId } }
        ?.let { return it }
    return units.firstOrNull { unit ->
        unit.state.status == SubjectLearningStatus.InProgress ||
            unit.lessons.any { it.state.status == SubjectLearningStatus.InProgress }
    }?.id ?: units.first().id
}

@Composable
private fun SubjectIdentityCard(data: StudentSubjectPage) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MasaryColors.brandNavy),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = Color.White.copy(alpha = 0.13f),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(16.dp)
                        .size(42.dp),
                    tint = MasaryColors.brandGoldBright,
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = data.identity.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                val label = data.identity.curriculumLabel.ifBlank { data.identity.versionType }
                if (label.isNotBlank()) {
                    Text(
                        text = label,
                        color = Color.White.copy(alpha = 0.78f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = when (data.content.structureMode) {
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle(stringResource(R.string.subject_progress))
            if (!data.points.available || data.points.value == null) {
                SupportingText(stringResource(R.string.subject_no_points_yet))
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Metric(
                        label = stringResource(R.string.subject_points),
                        value = data.points.value.toString(),
                        modifier = Modifier.weight(1f),
                    )
                    Metric(
                        label = stringResource(R.string.subject_level),
                        value = data.level.value
                            ?.takeIf { data.level.available }
                            ?.toString()
                            ?: "—",
                        modifier = Modifier.weight(1f),
                    )
                }
                if (data.progress.available && data.progress.percent != null) {
                    LinearProgressIndicator(
                        progress = { data.progress.percent.coerceIn(0, 100) / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = MasaryColors.brandGold,
                        trackColor = MasaryColors.border,
                    )
                    Text("${data.progress.percent}%", color = MasaryColors.muted)
                } else {
                    SupportingText(data.progress.reason)
                }
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
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = value,
                fontWeight = FontWeight.ExtraBold,
                color = MasaryColors.brandNavy,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MasaryColors.muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SubjectHeartsCard(data: StudentSubjectPage) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionTitle(stringResource(R.string.subject_hearts))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(data.hearts.maximum.coerceAtMost(6)) { index ->
                    Icon(
                        imageVector = if (index < data.hearts.current) {
                            Icons.Outlined.Favorite
                        } else {
                            Icons.Outlined.FavoriteBorder
                        },
                        contentDescription = null,
                        tint = if (index < data.hearts.current) MasaryColors.error else MasaryColors.muted,
                        modifier = Modifier.size(30.dp),
                    )
                }
                if (data.hearts.maximum > 6) {
                    Text(
                        text = "${data.hearts.current}/${data.hearts.maximum}",
                        color = MasaryColors.brandNavy,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            SupportingText(
                if (data.hearts.nextRestore.available && !data.hearts.nextRestore.at.isNullOrBlank()) {
                    data.hearts.nextRestore.at
                } else {
                    data.hearts.nextRestore.reason.ifBlank {
                        stringResource(R.string.subject_restore_unknown)
                    }
                },
            )
        }
    }
}

@Composable
private fun SubjectTrainingCard(
    data: StudentSubjectPage,
    onTrainingCenter: (Int) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MasaryColors.warmSurface)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.School, null, tint = MasaryColors.brandNavy)
                Spacer(Modifier.width(8.dp))
                SectionTitle(stringResource(R.string.subject_training_center))
            }
            Button(
                onClick = { onTrainingCenter(data.subjectVersionId) },
                enabled = data.actions.trainingCenter.available,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.subject_training_center))
            }
            if (!data.actions.trainingCenter.available) {
                SupportingText(data.actions.trainingCenter.reason)
            }
        }
    }
}

@Composable
private fun SubjectContentHeader(data: StudentSubjectPage) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.subject_content),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = MasaryColors.brandNavy,
        )
        if (data.content.hasParts && data.content.parts.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(end = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(data.content.parts, key = SubjectContentPart::partNumber) { part ->
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MasaryColors.iceSurface,
                        border = BorderStroke(1.dp, MasaryColors.border),
                    ) {
                        Text(
                            text = part.label,
                            modifier = Modifier
                                .widthIn(max = 180.dp)
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            color = MasaryColors.brandNavy,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SubjectPartCard(part: SubjectContentPart) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Outlined.MenuBook, null, tint = MasaryColors.brandGold)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = part.label,
                    fontWeight = FontWeight.Bold,
                    color = MasaryColors.brandNavy,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(
                        R.string.subject_part_counts,
                        part.unitsCount,
                        part.lessonsCount,
                    ),
                    color = MasaryColors.muted,
                )
            }
        }
    }
}

@Composable
private fun SubjectUnitCard(
    unit: SubjectUnit,
    partLabel: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    onLesson: (SubjectLesson) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.MenuBook,
                    contentDescription = null,
                    tint = MasaryColors.brandGold,
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = unit.title,
                        fontWeight = FontWeight.ExtraBold,
                        color = MasaryColors.brandNavy,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val lessonsCountLabel = stringResource(
                        R.string.subject_unit_lessons_count,
                        unit.lessons.size,
                    )
                    val subtitle = if (partLabel.isBlank()) {
                        lessonsCountLabel
                    } else {
                        "$partLabel • $lessonsCountLabel"
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MasaryColors.muted,
                    )
                    if (unit.state.status == SubjectLearningStatus.Locked && unit.state.reason.isNotBlank()) {
                        Text(
                            text = unit.state.reason,
                            style = MaterialTheme.typography.labelSmall,
                            color = MasaryColors.muted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (unit.state.status !in setOf(SubjectLearningStatus.Unknown, SubjectLearningStatus.Ready)) {
                    SubjectLearningStatePill(unit.state.status)
                    Spacer(Modifier.width(8.dp))
                }
                val chevronRotation by animateFloatAsState(
                    targetValue = if (expanded) 90f else 0f,
                    label = "subject-unit-chevron",
                )
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MasaryColors.iceSurface,
                    border = BorderStroke(1.dp, MasaryColors.border),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(8.dp)
                            .size(18.dp)
                            .rotate(chevronRotation),
                        tint = MasaryColors.brandNavy,
                    )
                }
            }

            if (expanded) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (unit.lessons.isEmpty()) {
                        SupportingText(stringResource(R.string.subject_unit_no_lessons))
                    } else {
                        unit.lessons.forEach { lesson ->
                            SubjectLessonRow(
                                lesson = lesson,
                                onClick = { onLesson(lesson) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubjectStandaloneLessonCard(
    lesson: SubjectLesson,
    partLabel: String,
    onLesson: () -> Unit,
) {
    val canOpen = lesson.preparation.available && lesson.unitId != null
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = canOpen, onClick = onLesson)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = lesson.title,
                        fontWeight = FontWeight.Bold,
                        color = MasaryColors.brandNavy,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (partLabel.isNotBlank()) {
                        Text(
                            text = partLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MasaryColors.muted,
                        )
                    }
                }
                if (lesson.state.status !in setOf(SubjectLearningStatus.Unknown, SubjectLearningStatus.Ready)) {
                    SubjectLearningStatePill(lesson.state.status)
                }
            }
        }
    }
}

@Composable
private fun SubjectLessonRow(
    lesson: SubjectLesson,
    onClick: () -> Unit,
) {
    val canOpen = lesson.preparation.available && lesson.unitId != null
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = canOpen, onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MasaryColors.iceSurface,
        border = BorderStroke(1.dp, MasaryColors.border),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = lesson.title,
                    color = MasaryColors.brandNavy,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (lesson.state.status == SubjectLearningStatus.Locked && lesson.state.reason.isNotBlank()) {
                    Text(
                        text = lesson.state.reason,
                        style = MaterialTheme.typography.labelSmall,
                        color = MasaryColors.muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (lesson.state.status !in setOf(SubjectLearningStatus.Unknown, SubjectLearningStatus.Ready)) {
                Spacer(Modifier.width(8.dp))
                SubjectLearningStatePill(lesson.state.status)
            }
        }
    }
}

@Composable
private fun SubjectLearningStatePill(status: SubjectLearningStatus) {
    val label = when (status) {
        SubjectLearningStatus.Ready -> stringResource(R.string.subject_state_ready)
        SubjectLearningStatus.InProgress -> stringResource(R.string.subject_state_in_progress)
        SubjectLearningStatus.Completed -> stringResource(R.string.subject_state_completed)
        SubjectLearningStatus.Locked -> stringResource(R.string.subject_state_locked)
        SubjectLearningStatus.Unavailable -> stringResource(R.string.subject_state_unavailable)
        SubjectLearningStatus.Unknown -> stringResource(R.string.subject_state_unknown)
    }
    val background = when (status) {
        SubjectLearningStatus.Ready -> MasaryColors.iceSurface
        SubjectLearningStatus.InProgress -> MasaryColors.warmSurface
        SubjectLearningStatus.Completed -> MasaryColors.success.copy(alpha = 0.10f)
        SubjectLearningStatus.Locked,
        SubjectLearningStatus.Unavailable,
        SubjectLearningStatus.Unknown,
        -> MasaryColors.iceSurface
    }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = background,
        border = BorderStroke(1.dp, MasaryColors.border),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MasaryColors.brandNavy,
            maxLines = 1,
        )
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
            text = reason.ifBlank { stringResource(R.string.subject_unavailable) },
            modifier = Modifier.padding(16.dp),
            color = MasaryColors.muted,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SubjectLastActivityCard(
    data: StudentSubjectPage,
    onContinue: () -> Unit,
) {
    val unit = data.lastActivity.unitId?.let { unitId ->
        data.content.units.firstOrNull { it.id == unitId }
    }
    val lesson = data.lastActivity.lessonId?.let { lessonId ->
        (unit?.lessons.orEmpty() + data.content.lessons).firstOrNull { it.id == lessonId }
    }
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.History, null, tint = MasaryColors.brandGold)
                Spacer(Modifier.width(8.dp))
                SectionTitle(stringResource(R.string.subject_last_activity))
            }
            if (data.lastActivity.available) {
                if (unit != null) {
                    Text(
                        text = stringResource(R.string.subject_last_activity_unit_named, unit.title),
                        color = MasaryColors.brandNavy,
                        fontWeight = FontWeight.SemiBold,
                    )
                } else {
                    data.lastActivity.unitId?.let { unitId ->
                        Text(
                            text = stringResource(R.string.subject_last_activity_unit, unitId),
                            color = MasaryColors.brandNavy,
                        )
                    }
                }
                lesson?.let {
                    Text(
                        text = stringResource(R.string.subject_last_activity_lesson_named, it.title),
                        color = MasaryColors.brandNavy,
                    )
                }
                data.lastActivity.mode.takeIf(String::isNotBlank)?.let { mode ->
                    Text(
                        text = stringResource(
                            R.string.subject_last_activity_mode,
                            localizedActivityMode(mode),
                        ),
                        color = MasaryColors.muted,
                    )
                }
                data.lastActivity.updatedAt.takeIf(String::isNotBlank)?.let { updatedAt ->
                    Text(
                        text = updatedAt,
                        color = MasaryColors.muted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (
                    data.lastActivity.preparation.available &&
                    data.lastActivity.unitId != null &&
                    data.lastActivity.lessonId != null
                ) {
                    Button(
                        onClick = onContinue,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.subject_continue_last_activity))
                    }
                } else if (!data.lastActivity.preparation.available) {
                    SupportingText(data.lastActivity.preparation.reason)
                }
            } else {
                SupportingText(
                    data.lastActivity.reason.ifBlank {
                        stringResource(R.string.subject_no_last_activity)
                    },
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = MasaryColors.brandNavy,
    )
}

@Composable
private fun SupportingText(text: String) {
    Text(
        text = text.ifBlank { stringResource(R.string.subject_unavailable) },
        color = MasaryColors.muted,
    )
}

@Composable
private fun SubjectBanner(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MasaryColors.iceSurface,
        border = BorderStroke(1.dp, MasaryColors.border),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(12.dp),
            color = MasaryColors.brandNavy,
        )
    }
}

@Composable
private fun SubjectLoading(onBack: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.subject_back),
            )
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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MasaryBrandMark(size = 64.dp)
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = message,
                    color = MasaryColors.muted,
                    textAlign = TextAlign.Center,
                )
                onRetry?.let { retry ->
                    Button(onClick = retry, modifier = Modifier.fillMaxWidth()) {
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

private fun formatSnapshotTime(epochMillis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(epochMillis))

private fun localizedActivityMode(mode: String): String = when (mode.trim().lowercase()) {
    "learn", "learning" -> "تعلّم"
    "review" -> "مراجعة"
    "fill" -> "إكمال"
    "connect", "match" -> "توصيل"
    "choose", "mcq" -> "اختيار"
    "truefalse", "true_false" -> "صح أو خطأ"
    "speed" -> "سرعة"
    else -> mode.trim()
}
