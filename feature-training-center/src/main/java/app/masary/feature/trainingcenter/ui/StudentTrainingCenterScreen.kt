package app.masary.feature.trainingcenter.ui

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
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import app.masary.core.ui.MasaryBrandMark
import app.masary.core.ui.MasaryColors
import app.masary.feature.trainingcenter.R
import app.masary.feature.trainingcenter.domain.StudentTrainingCenter
import app.masary.feature.trainingcenter.domain.TrainingCenterRepository
import app.masary.feature.trainingcenter.domain.TrainingCenterTool
import app.masary.feature.trainingcenter.domain.TrainingToolKey
import java.text.DateFormat
import java.util.Date

@Composable
fun StudentTrainingCenterRoute(
    subjectVersionId: Int,
    repository: TrainingCenterRepository,
    onBack: () -> Unit,
    onSessionExpired: () -> Unit,
    onTool: (TrainingCenterTool) -> Unit,
) {
    val model: StudentTrainingCenterViewModel = viewModel(
        key = "training-center-$subjectVersionId",
        factory = StudentTrainingCenterViewModelFactory(subjectVersionId, repository),
    )
    val state by model.state.collectAsStateWithLifecycle()

    LaunchedEffect(state) {
        if (state == TrainingCenterUiState.SessionExpired) onSessionExpired()
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        when (val current = state) {
            TrainingCenterUiState.Loading,
            TrainingCenterUiState.SessionExpired,
            -> TrainingCenterLoading(onBack)

            is TrainingCenterUiState.NotFound -> TrainingCenterMessage(
                title = stringResource(R.string.training_center_not_found),
                message = current.message,
                onBack = onBack,
                onRetry = null,
            )

            is TrainingCenterUiState.Error -> TrainingCenterMessage(
                title = stringResource(R.string.training_center_error),
                message = current.message,
                onBack = onBack,
                onRetry = model::refresh,
            )

            is TrainingCenterUiState.Content -> TrainingCenterContent(
                data = current.data,
                isRefreshing = current.isRefreshing,
                refreshMessage = current.refreshMessage,
                onRefresh = model::refresh,
                onBack = onBack,
                onTool = onTool,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrainingCenterContent(
    data: StudentTrainingCenter,
    isRefreshing: Boolean,
    refreshMessage: String?,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    onTool: (TrainingCenterTool) -> Unit,
) {
    Scaffold(
        containerColor = MasaryColors.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.training_center_title),
                            fontWeight = FontWeight.Bold,
                            color = MasaryColors.brandNavy,
                        )
                        Text(
                            text = data.identity.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MasaryColors.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.training_center_back),
                        )
                    }
                },
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
                item { TrainingCenterHero(data) }
                data.snapshot?.let { snapshot ->
                    item {
                        TrainingCenterBanner(
                            stringResource(
                                R.string.training_center_cached_at,
                                formatSnapshotTime(snapshot.savedAtEpochMillis),
                            ),
                        )
                    }
                }
                refreshMessage?.takeIf(String::isNotBlank)?.let { message ->
                    item { TrainingCenterBanner(message) }
                }
                items(data.tools, key = { it.key.wireKey }) { tool ->
                    TrainingToolCard(tool = tool, onTool = onTool)
                }
            }
        }
    }
}

@Composable
private fun TrainingCenterHero(data: StudentTrainingCenter) {
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
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(16.dp)
                        .size(40.dp),
                    tint = MasaryColors.brandGoldBright,
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = data.identity.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                data.identity.curriculumLabel.takeIf(String::isNotBlank)?.let { curriculum ->
                    Text(
                        text = curriculum,
                        color = Color.White.copy(alpha = 0.78f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = stringResource(R.string.training_center_subtitle),
                    color = MasaryColors.brandGoldBright,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun TrainingToolCard(
    tool: TrainingCenterTool,
    onTool: (TrainingCenterTool) -> Unit,
) {
    val container = if (tool.available) Color.White else MasaryColors.iceSurface
    Card(
        colors = CardDefaults.cardColors(containerColor = container),
        border = BorderStroke(1.dp, MasaryColors.border),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = if (tool.available) MasaryColors.warmSurface else MasaryColors.background,
                ) {
                    Icon(
                        imageVector = tool.key.icon,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(12.dp)
                            .size(28.dp),
                        tint = if (tool.available) MasaryColors.brandGold else MasaryColors.muted,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = tool.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MasaryColors.brandNavy,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = tool.description,
                        color = MasaryColors.muted,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            tool.itemCount?.let { count ->
                Text(
                    text = stringResource(
                        if (tool.key == TrainingToolKey.SmartReview) {
                            R.string.training_center_mistakes_count
                        } else {
                            R.string.training_center_questions_count
                        },
                        count,
                    ),
                    color = MasaryColors.brandNavy,
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            if (tool.available) {
                Button(
                    onClick = { onTool(tool) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.training_center_start))
                }
            } else {
                Text(
                    text = tool.reason.ifBlank {
                        stringResource(R.string.training_center_unavailable)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    color = MasaryColors.muted,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private val TrainingToolKey.icon: ImageVector
    get() = when (this) {
        TrainingToolKey.Choose -> Icons.Outlined.RadioButtonChecked
        TrainingToolKey.TrueFalse -> Icons.Outlined.CheckCircleOutline
        TrainingToolKey.Connect -> Icons.Outlined.Hub
        TrainingToolKey.Fill -> Icons.Outlined.EditNote
        TrainingToolKey.Speed -> Icons.Outlined.Bolt
        TrainingToolKey.SmartReview -> Icons.Outlined.AutoAwesome
    }

@Composable
private fun TrainingCenterBanner(message: String) {
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
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun TrainingCenterLoading(onBack: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.training_center_back),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            MasaryBrandMark(size = 72.dp)
            Spacer(Modifier.height(16.dp))
            CircularProgressIndicator(color = MasaryColors.brandGold)
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.training_center_loading),
                color = MasaryColors.muted,
            )
        }
    }
}

@Composable
private fun TrainingCenterMessage(
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
                        Text(stringResource(R.string.training_center_retry))
                    }
                }
                OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.training_center_back))
                }
            }
        }
    }
}

private fun formatSnapshotTime(epochMillis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(epochMillis))
