package app.masary.feature.questionsession.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.masary.core.ui.MasaryColors
import app.masary.feature.questionsession.domain.ConnectAnswerPair
import app.masary.feature.questionsession.domain.QuestionAnswerInput
import app.masary.feature.questionsession.domain.QuestionItem
import app.masary.feature.questionsession.domain.QuestionPayload
import app.masary.feature.questionsession.domain.QuestionSessionPackage
import app.masary.feature.questionsession.domain.QuestionSessionRepository
import app.masary.feature.questionsession.domain.QuestionSessionResult
import app.masary.feature.questionsession.domain.QuestionType

@Composable
fun QuestionSessionRoute(
    sessionId: String,
    repository: QuestionSessionRepository,
    onBack: () -> Unit,
) {
    val model: QuestionSessionViewModel = viewModel(
        key = "question-session-${sessionId}",
        factory = QuestionSessionViewModelFactory(sessionId, repository),
    )
    val state by model.state.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)

    QuestionSessionScreen(
        state = state,
        onBack = onBack,
        onRetry = model::retry,
        onSubmit = model::submit,
        onPrevious = model::previous,
        onNext = model::next,
        onFinish = model::finish,
        onUserActivity = model::markUserActivity,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuestionSessionScreen(
    state: QuestionSessionUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSubmit: (QuestionAnswerInput) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onFinish: () -> Unit,
    onUserActivity: () -> Unit,
) {
    CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            modifier = Modifier.pointerInput(onUserActivity) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent()
                        onUserActivity()
                    }
                }
            },
            containerColor = MasaryColors.background,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "جلسة الأسئلة",
                            fontWeight = FontWeight.Bold,
                            color = MasaryColors.brandNavy,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "رجوع")
                        }
                    },
                )
            },
        ) { paddingValues ->
            when (state) {
                QuestionSessionUiState.Loading -> LoadingState(
                    modifier = Modifier.padding(paddingValues),
                )

                is QuestionSessionUiState.Content -> QuestionContent(
                    data = state.data,
                    viewingIndex = state.viewingIndex,
                    remainingSeconds = state.remainingSeconds,
                    isRefreshing = state.isRefreshing,
                    isSaving = state.isSaving,
                    message = state.message,
                    onSubmit = onSubmit,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    onFinish = onFinish,
                    modifier = Modifier.padding(paddingValues),
                )

                is QuestionSessionUiState.CompletedLocal -> CompletedState(
                    message = state.message,
                    onBack = onBack,
                    modifier = Modifier.padding(paddingValues),
                )

                is QuestionSessionUiState.Result -> ConfirmedResultState(
                    result = state.data,
                    onBack = onBack,
                    modifier = Modifier.padding(paddingValues),
                )

                is QuestionSessionUiState.Error -> MessageState(
                    title = "تعذر فتح جلسة الأسئلة",
                    body = state.message,
                    action = "إعادة المحاولة",
                    onAction = onRetry,
                    modifier = Modifier.padding(paddingValues),
                )

                is QuestionSessionUiState.Expired -> MessageState(
                    title = "انتهت الجلسة",
                    body = state.message,
                    action = "العودة",
                    onAction = onBack,
                    modifier = Modifier.padding(paddingValues),
                )

                is QuestionSessionUiState.SourceUnavailable -> MessageState(
                    title = "الأسئلة غير متاحة الآن",
                    body = state.message,
                    action = "العودة",
                    onAction = onBack,
                    modifier = Modifier.padding(paddingValues),
                )
            }
        }
    }
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = MasaryColors.brandGold)
            Spacer(Modifier.height(14.dp))
            Text("نجهّز أسئلة الجلسة…", color = MasaryColors.muted)
        }
    }
}

@Composable
private fun QuestionContent(
    data: QuestionSessionPackage,
    viewingIndex: Int,
    remainingSeconds: Int?,
    isRefreshing: Boolean,
    isSaving: Boolean,
    message: String?,
    onSubmit: (QuestionAnswerInput) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val index = viewingIndex.coerceIn(0, data.questions.lastIndex)
    val question = data.questions[index]
    val shownNumber = index + 1
    val answeredFrontier = data.progress.currentIndex.coerceIn(0, data.questions.size)
    val progress = answeredFrontier.toFloat() /
        data.questions.size.coerceAtLeast(1).toFloat()
    val revisitingAnswered = index < answeredFrontier
    val maxBrowsableIndex = minOf(answeredFrontier, data.questions.lastIndex)
    val canGoPrevious = data.policy.allowBack && index > 0
    val canGoNext = index < maxBrowsableIndex
    val canFinish = answeredFrontier >= data.questions.size

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "السؤال $shownNumber من ${data.questions.size}",
                    color = MasaryColors.brandNavy,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (remainingSeconds != null && data.policy.timerSeconds > 0) {
                    val minutes = remainingSeconds / 60
                    val seconds = remainingSeconds % 60
                    Text(
                        text = String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds),
                        color = if (remainingSeconds <= 30) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MasaryColors.brandNavy
                        },
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(18.dp),
                        strokeWidth = 2.dp,
                        color = MasaryColors.brandGold,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = MasaryColors.brandGold,
                trackColor = MasaryColors.border,
            )
        }

        message?.takeIf(String::isNotBlank)?.let { text ->
            item {
                Text(
                    text = text,
                    color = MasaryColors.muted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        if (revisitingAnswered) {
            item {
                Text(
                    text = "سبق تثبيت إجابة لهذا السؤال. يمكنك تثبيت إجابة جديدة لاستبدالها.",
                    color = MasaryColors.muted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = question.prompt,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MasaryColors.brandNavy,
                    )
                    QuestionRenderer(
                        question = question,
                        enabled = !isSaving,
                        onSubmit = onSubmit,
                    )
                    if (data.policy.allowSkip) {
                        OutlinedButton(
                            onClick = { onSubmit(QuestionAnswerInput.Skip) },
                            enabled = !isSaving,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("تخطي السؤال")
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (canGoPrevious) {
                    OutlinedButton(
                        onClick = onPrevious,
                        enabled = !isSaving,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("السابق")
                    }
                }
                if (canGoNext) {
                    OutlinedButton(
                        onClick = onNext,
                        enabled = !isSaving,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("التالي")
                    }
                }
            }
        }

        if (canFinish) {
            item {
                Button(
                    onClick = onFinish,
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (isSaving) "جارٍ الإنهاء…" else "إنهاء الاختبار")
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun QuestionRenderer(
    question: QuestionItem,
    enabled: Boolean,
    onSubmit: (QuestionAnswerInput) -> Unit,
) {
    when (question.type) {
        QuestionType.Choose,
        QuestionType.Speed,
        -> OptionsRenderer(
            payload = question.payload as QuestionPayload.Options,
            enabled = enabled,
            onSubmit = onSubmit,
        )

        QuestionType.TrueFalse -> TrueFalseRenderer(
            payload = question.payload as QuestionPayload.TrueFalse,
            enabled = enabled,
            onSubmit = onSubmit,
        )

        QuestionType.Fill -> FillRenderer(
            payload = question.payload as QuestionPayload.Fill,
            enabled = enabled,
            onSubmit = onSubmit,
        )

        QuestionType.Direct -> DirectRenderer(
            enabled = enabled,
            onSubmit = onSubmit,
        )

        QuestionType.Connect -> ConnectRenderer(
            payload = question.payload as QuestionPayload.Connect,
            enabled = enabled,
            onSubmit = onSubmit,
        )
    }
}

@Composable
private fun OptionsRenderer(
    payload: QuestionPayload.Options,
    enabled: Boolean,
    onSubmit: (QuestionAnswerInput) -> Unit,
) {
    var selected by remember(payload.options) { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        payload.options.forEach { option ->
            Card(
                onClick = { if (enabled) selected = option.id },
                enabled = enabled,
                colors = CardDefaults.cardColors(containerColor = MasaryColors.iceSurface),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = selected == option.id,
                        onClick = { if (enabled) selected = option.id },
                        enabled = enabled,
                    )
                    Text(
                        text = option.text,
                        color = MasaryColors.brandNavy,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Button(
            onClick = { selected?.let { onSubmit(QuestionAnswerInput.Choice(it)) } },
            enabled = enabled && selected != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (!enabled) {
                Text("جارٍ الحفظ…")
            } else {
                Text("تثبيت الإجابة والمتابعة")
            }
        }
    }
}

@Composable
private fun TrueFalseRenderer(
    payload: QuestionPayload.TrueFalse,
    enabled: Boolean,
    onSubmit: (QuestionAnswerInput) -> Unit,
) {
    var selected by remember(payload.options) { mutableStateOf<String?>(null) }
    var selectedReason by remember(payload.reasons) { mutableStateOf<String?>(null) }
    val falseOptionId = payload.options
        .firstOrNull { it.text.trim() == "خطأ" }
        ?.id
    val reasonRequiredNow = payload.requiresReason &&
        (!payload.reasonOnlyOnFalse || selected == falseOptionId)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        payload.options.forEach { option ->
            Card(
                onClick = {
                    if (enabled) {
                        selected = option.id
                        if (payload.reasonOnlyOnFalse && option.id != falseOptionId) {
                            selectedReason = null
                        }
                    }
                },
                enabled = enabled,
                colors = CardDefaults.cardColors(containerColor = MasaryColors.iceSurface),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = selected == option.id,
                        onClick = {
                            if (enabled) {
                                selected = option.id
                                if (payload.reasonOnlyOnFalse && option.id != falseOptionId) {
                                    selectedReason = null
                                }
                            }
                        },
                        enabled = enabled,
                    )
                    Text(
                        text = option.text,
                        color = MasaryColors.brandNavy,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        if (reasonRequiredNow) {
            Text(
                text = "اختر السبب",
                fontWeight = FontWeight.Bold,
                color = MasaryColors.brandNavy,
            )
            payload.reasons.forEach { reason ->
                FilterChip(
                    selected = selectedReason == reason.id,
                    enabled = enabled,
                    onClick = { if (enabled) selectedReason = reason.id },
                    label = { Text(reason.text) },
                )
            }
        }

        Button(
            onClick = {
                selected?.let {
                    onSubmit(
                        QuestionAnswerInput.Choice(
                            optionId = it,
                            reasonId = selectedReason,
                        ),
                    )
                }
            },
            enabled = enabled &&
                selected != null &&
                (!reasonRequiredNow || selectedReason != null),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (enabled) "تثبيت الإجابة والمتابعة" else "جارٍ الحفظ…")
        }
    }
}

@Composable
private fun FillRenderer(
    payload: QuestionPayload.Fill,
    enabled: Boolean,
    onSubmit: (QuestionAnswerInput) -> Unit,
) {
    var values by remember(payload.blanksCount) {
        mutableStateOf(List(payload.blanksCount.coerceIn(1, 4)) { "" })
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        values.forEachIndexed { index, value ->
            OutlinedTextField(
                value = value,
                onValueChange = { updated ->
                    values = values.toMutableList().also { it[index] = updated }
                },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text(
                        if (values.size == 1) "اكتب الإجابة"
                        else "الفراغ " + (index + 1),
                    )
                },
                singleLine = false,
            )
        }
        Button(
            onClick = { onSubmit(QuestionAnswerInput.Fill(values)) },
            enabled = enabled && values.all(String::isNotBlank),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (enabled) "تثبيت الإجابة والمتابعة" else "جارٍ الحفظ…")
        }
    }
}

@Composable
private fun DirectRenderer(
    enabled: Boolean,
    onSubmit: (QuestionAnswerInput) -> Unit,
) {
    var value by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("اكتب الإجابة") },
            singleLine = false,
        )
        Button(
            onClick = { onSubmit(QuestionAnswerInput.Text(value)) },
            enabled = enabled && value.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (enabled) "تثبيت الإجابة والمتابعة" else "جارٍ الحفظ…")
        }
    }
}

@Composable
private fun ConnectRenderer(
    payload: QuestionPayload.Connect,
    enabled: Boolean,
    onSubmit: (QuestionAnswerInput) -> Unit,
) {
    var selectedPairs by remember(payload.leftItems, payload.rightItems) {
        mutableStateOf<Map<String, String>>(emptyMap())
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        payload.leftItems.forEach { left ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = left.text,
                    fontWeight = FontWeight.Bold,
                    color = MasaryColors.brandNavy,
                )
                payload.rightItems.forEach { right ->
                    val selected = selectedPairs[left.id] == right.id
                    val usedElsewhere = selectedPairs.any { (leftId, rightId) ->
                        leftId != left.id && rightId == right.id
                    }
                    FilterChip(
                        selected = selected,
                        enabled = enabled && (!usedElsewhere || selected),
                        onClick = {
                            if (enabled) {
                                selectedPairs = selectedPairs
                                    .filterValues { it != right.id }
                                    .toMutableMap()
                                    .apply { put(left.id, right.id) }
                            }
                        },
                        label = { Text(right.text) },
                    )
                }
            }
        }

        val complete = selectedPairs.size == payload.leftItems.size &&
            selectedPairs.values.toSet().size == payload.rightItems.size
        Button(
            onClick = {
                onSubmit(
                    QuestionAnswerInput.Connections(
                        payload.leftItems.mapNotNull { left ->
                            selectedPairs[left.id]?.let { right ->
                                ConnectAnswerPair(left.id, right)
                            }
                        },
                    ),
                )
            },
            enabled = enabled && complete,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (enabled) "تثبيت التوصيل والمتابعة" else "جارٍ الحفظ…")
        }
    }
}

@Composable
private fun ConfirmedResultState(
    result: QuestionSessionResult,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val policy = result.policy.result
    val wrongAnswers = (result.score.incorrectAnswers - result.score.partialAnswers).coerceAtLeast(0)
    val earnedXp = if (kotlin.math.abs(result.score.xpEarned - result.score.xpEarned.toInt()) < 0.0001) {
        result.score.xpEarned.toInt().toString()
    } else {
        String.format(java.util.Locale.US, "%.2f", result.score.xpEarned).trimEnd('0').trimEnd('.')
    }

    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = MasaryColors.brandGold,
                )
                Text(
                    "تم تأكيد النتيجة",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MasaryColors.brandNavy,
                    textAlign = TextAlign.Center,
                )
                if (policy.showPassBadge) {
                    Text(
                        text = if (result.score.passed) "ناجح" else "تحتاج محاولة أخرى",
                        fontWeight = FontWeight.Bold,
                        color = MasaryColors.brandNavy,
                    )
                }
                if (policy.showScore) {
                    Text(
                        text = "${result.score.scorePercent}%",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MasaryColors.brandNavy,
                    )
                }
                if (policy.showCountsCorrect) {
                    Text(
                        text = "الإجابات الصحيحة: ${result.score.correctAnswers}",
                        color = MasaryColors.brandNavy,
                    )
                }
                if (policy.showCountsPartial && result.score.partialAnswers > 0) {
                    Text(
                        text = "الإجابات الجزئية: ${result.score.partialAnswers}",
                        color = MasaryColors.brandNavy,
                    )
                }
                if (policy.showCountsWrong) {
                    Text(
                        text = "الإجابات الخاطئة: $wrongAnswers",
                        color = MasaryColors.brandNavy,
                    )
                }
                Text(
                    text = "إجمالي الأسئلة: ${result.score.totalQuestions}",
                    color = MasaryColors.muted,
                )
                if (result.score.timedOut) {
                    Text(
                        text = "انتهى وقت الاختبار؛ احتُسبت الأسئلة غير المجابة بصفر.",
                        color = MasaryColors.muted,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                }
                if (policy.showXp) {
                    Text(
                        text = "XP المكتسب: +$earnedXp",
                        color = MasaryColors.brandNavy,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (policy.showHeartsSpent && result.score.heartsSpent > 0) {
                    Text(
                        text = "القلوب المصروفة: ${result.score.heartsSpent}",
                        color = MasaryColors.muted,
                    )
                }
                if (!result.confirmedDeltaAvailable &&
                    result.confirmedDeltaReason.isNotBlank()
                ) {
                    Text(
                        text = result.confirmedDeltaReason,
                        color = MasaryColors.muted,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                }
                if (policy.showBackButton) {
                    OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                        Text("العودة")
                    }
                }
            }
        }
    }
}

@Composable
private fun CompletedState(
    message: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = MasaryColors.brandGold,
                )
                Text(
                    "أكملت الأسئلة على هذا الجهاز",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    message,
                    color = MasaryColors.muted,
                    textAlign = TextAlign.Center,
                )
                OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                    Text("العودة")
                }
            }
        }
    }
}

@Composable
private fun MessageState(
    title: String,
    body: String,
    action: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(body, color = MasaryColors.muted, textAlign = TextAlign.Center)
                Button(onClick = onAction, modifier = Modifier.fillMaxWidth()) {
                    Text(action)
                }
            }
        }
    }
}
