package se.oscarb.bdai.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bdai.shared.generated.resources.Res
import bdai.shared.generated.resources.app_name
import bdai.shared.generated.resources.cd_new_chat
import bdai.shared.generated.resources.cd_open_people
import bdai.shared.generated.resources.cd_open_settings
import bdai.shared.generated.resources.cd_send_message
import bdai.shared.generated.resources.cd_stop_listening
import bdai.shared.generated.resources.cd_voice_input
import bdai.shared.generated.resources.chat_error_empty_answer
import bdai.shared.generated.resources.chat_error_generic
import bdai.shared.generated.resources.chat_greeting_subtitle
import bdai.shared.generated.resources.chat_greeting_title
import bdai.shared.generated.resources.chat_input_placeholder
import bdai.shared.generated.resources.example_prompt_1
import bdai.shared.generated.resources.example_prompt_2
import bdai.shared.generated.resources.example_prompt_3
import bdai.shared.generated.resources.example_prompts_title
import bdai.shared.generated.resources.pill_add_label
import bdai.shared.generated.resources.pill_add_prefill
import bdai.shared.generated.resources.pill_browse_label
import bdai.shared.generated.resources.pill_kids_label
import bdai.shared.generated.resources.pill_kids_prompt
import bdai.shared.generated.resources.pill_upcoming_label
import bdai.shared.generated.resources.pill_upcoming_prompt
import bdai.shared.generated.resources.sync_status_pending
import bdai.shared.generated.resources.sync_status_synced
import bdai.shared.generated.resources.upcoming_in_days_badge
import bdai.shared.generated.resources.upcoming_soon_card_title
import bdai.shared.generated.resources.upcoming_today_badge
import bdai.shared.generated.resources.voice_listening_auto
import bdai.shared.generated.resources.voice_listening_en
import bdai.shared.generated.resources.voice_listening_placeholder
import bdai.shared.generated.resources.voice_listening_sv
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import se.oscarb.bdai.agent.domain.ModelState
import se.oscarb.bdai.core.designsystem.AppIcons
import se.oscarb.bdai.core.designsystem.AppTheme
import se.oscarb.bdai.core.designsystem.GeminiSparkleBrush
import se.oscarb.bdai.domain.FlatBirthdayPerson
import se.oscarb.bdai.domain.VoiceLanguage
import se.oscarb.bdai.voice.rememberMicPermissionRequester

@Composable
fun BdaiChatScreenRoot(
    viewModel: BdaiViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    BdaiChatScreen(
        state = state,
        onAction = viewModel::onAction
    )
}

@Composable
fun BdaiChatScreen(
    state: BdaiChatState,
    onAction: (BdaiAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val requestMicPermission = rememberMicPermissionRequester { granted ->
        if (granted) {
            onAction(BdaiAction.OnStartVoice)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            BdaiTopBar(
                upcomingBadgeCount = state.upcomingBadgeCount,
                hasPendingSync = state.settings.hasPendingLocalChanges,
                showNewChatButton = state.messages.isNotEmpty(),
                onPeopleClick = { onAction(BdaiAction.OnOpenPeopleSheet) },
                onNewChatClick = { onAction(BdaiAction.OnNewChatClick) },
                onSettingsClick = { onAction(BdaiAction.OnOpenSettings) }
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                SuggestionPillsBar(
                    onSendPrompt = { onAction(BdaiAction.OnSendPrompt(it)) },
                    onPrefillInput = { onAction(BdaiAction.OnPrefillInput(it)) },
                    onBrowsePeople = { onAction(BdaiAction.OnOpenPeopleSheet) }
                )
                GeminiChatInputBar(
                    inputState = state.input,
                    isModelReady = state.isModelReady,
                    isListening = state.isListening,
                    voiceLanguage = state.settings.voiceLanguage,
                    canSend = state.canSend,
                    onMicClick = {
                        if (state.isListening) {
                            onAction(BdaiAction.OnStopVoice)
                        } else {
                            requestMicPermission()
                        }
                    },
                    onToggleVoiceLanguage = {
                        onAction(BdaiAction.OnCycleVoiceLanguage)
                    },
                    onSendClick = { onAction(BdaiAction.OnSendClick) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                !state.isModelReady -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        ModelSetupCard(
                            modelState = state.modelState,
                            onDownloadClick = { onAction(BdaiAction.OnDownloadModelClick) },
                            onRetryLoadClick = { onAction(BdaiAction.OnRetryLoadModelClick) }
                        )
                    }
                }
                state.messages.isEmpty() && !state.isGenerating -> {
                    GeminiGreetingEmptyState(
                        upcomingWithin7Days = state.upcomingWithin7Days.take(2),
                        onSendPrompt = { onAction(BdaiAction.OnSendPrompt(it)) },
                        onOpenPeopleClick = { onAction(BdaiAction.OnOpenPeopleSheet) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                else -> {
                    ChatMessageList(
                        messages = state.messages,
                        isGenerating = state.isGenerating,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    if (state.isPeopleSheetVisible) {
        PeopleSheet(
            state = state,
            onAction = onAction
        )
    }

    if (state.isSettingsVisible) {
        SettingsDialog(
            state = state,
            onAction = onAction
        )
    }
}

@Composable
private fun BdaiTopBar(
    upcomingBadgeCount: Int,
    hasPendingSync: Boolean,
    showNewChatButton: Boolean,
    onPeopleClick: () -> Unit,
    onNewChatClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                painter = AppIcons.Sparkle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.app_name),
                    style = MaterialTheme.typography.titleLarge.merge(
                        TextStyle(brush = GeminiSparkleBrush, fontWeight = FontWeight.Bold)
                    )
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(
                                color = if (hasPendingSync) {
                                    MaterialTheme.colorScheme.tertiary
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                                shape = CircleShape
                            )
                    )
                    Text(
                        text = stringResource(
                            if (hasPendingSync) Res.string.sync_status_pending else Res.string.sync_status_synced
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (showNewChatButton) {
                IconButton(onClick = onNewChatClick) {
                    Icon(
                        painter = AppIcons.Refresh,
                        contentDescription = stringResource(Res.string.cd_new_chat)
                    )
                }
            }

            BadgedBox(
                badge = {
                    if (upcomingBadgeCount > 0) {
                        Badge(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ) {
                            Text(text = upcomingBadgeCount.toString())
                        }
                    }
                }
            ) {
                FilledTonalIconButton(onClick = onPeopleClick) {
                    Icon(
                        painter = AppIcons.Cake,
                        contentDescription = stringResource(Res.string.cd_open_people)
                    )
                }
            }

            IconButton(onClick = onSettingsClick) {
                Icon(
                    painter = AppIcons.Settings,
                    contentDescription = stringResource(Res.string.cd_open_settings)
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    }
}

@Composable
private fun GeminiGreetingEmptyState(
    upcomingWithin7Days: List<FlatBirthdayPerson>,
    onSendPrompt: (String) -> Unit,
    onOpenPeopleClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val prompt1 = stringResource(Res.string.example_prompt_1)
    val prompt2 = stringResource(Res.string.example_prompt_2)
    val prompt3 = stringResource(Res.string.example_prompt_3)

    Column(
        modifier = modifier
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = AppIcons.Sparkle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(44.dp)
        )
        Text(
            text = stringResource(Res.string.chat_greeting_title),
            style = MaterialTheme.typography.headlineSmall.merge(
                TextStyle(brush = GeminiSparkleBrush, fontWeight = FontWeight.Bold)
            ),
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(Res.string.chat_greeting_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        if (upcomingWithin7Days.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenPeopleClick),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            painter = AppIcons.Cake,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(Res.string.upcoming_soon_card_title),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    upcomingWithin7Days.forEach { person ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = person.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            val badge = if (person.daysUntilNextBirthday == 0) {
                                stringResource(Res.string.upcoming_today_badge, person.turningAge)
                            } else {
                                stringResource(
                                    Res.string.upcoming_in_days_badge,
                                    person.daysUntilNextBirthday,
                                    person.turningAge
                                )
                            }
                            Text(
                                text = badge,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(Res.string.example_prompts_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            listOf(prompt1, prompt2, prompt3).forEach { example ->
                val cleanPrompt = example.trim('"')
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSendPrompt(cleanPrompt) }
                ) {
                    Text(
                        text = example,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SuggestionPillsBar(
    onSendPrompt: (String) -> Unit,
    onPrefillInput: (String) -> Unit,
    onBrowsePeople: () -> Unit
) {
    val upcomingPrompt = stringResource(Res.string.pill_upcoming_prompt)
    val kidsPrompt = stringResource(Res.string.pill_kids_prompt)
    val addPrefill = stringResource(Res.string.pill_add_prefill)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SuggestionPill(
            label = stringResource(Res.string.pill_upcoming_label),
            onClick = { onSendPrompt(upcomingPrompt) }
        )
        SuggestionPill(
            label = stringResource(Res.string.pill_kids_label),
            onClick = { onSendPrompt(kidsPrompt) }
        )
        SuggestionPill(
            label = stringResource(Res.string.pill_add_label),
            onClick = { onPrefillInput(addPrefill) }
        )
        SuggestionPill(
            label = stringResource(Res.string.pill_browse_label),
            onClick = onBrowsePeople
        )
    }
}

@Composable
private fun SuggestionPill(
    label: String,
    onClick: () -> Unit
) {
    AssistChip(
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge
            )
        },
        shape = CircleShape,
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            labelColor = MaterialTheme.colorScheme.onSurface
        ),
        border = AssistChipDefaults.assistChipBorder(
            enabled = true,
            borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    )
}

@Composable
private fun GeminiChatInputBar(
    inputState: TextFieldState,
    isModelReady: Boolean,
    isListening: Boolean,
    voiceLanguage: VoiceLanguage,
    canSend: Boolean,
    onMicClick: () -> Unit,
    onToggleVoiceLanguage: () -> Unit,
    onSendClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pulseTransition = rememberInfiniteTransition()
    val micScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening) 1.18f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 550),
            repeatMode = RepeatMode.Reverse
        )
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            OutlinedTextField(
                state = inputState,
                enabled = isModelReady,
                placeholder = {
                    val listeningRes = when (voiceLanguage) {
                        VoiceLanguage.AUTO -> Res.string.voice_listening_auto
                        VoiceLanguage.SWEDISH -> Res.string.voice_listening_sv
                        VoiceLanguage.ENGLISH -> Res.string.voice_listening_en
                    }
                    Text(
                        text = stringResource(
                            if (isListening) listeningRes
                            else Res.string.chat_input_placeholder
                        )
                    )
                },
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    disabledBorderColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent
                ),
                lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 4),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Send
                ),
                onKeyboardAction = { onSendClick() },
                modifier = Modifier.weight(1f)
            )

            // Quick toggle chip for voice recognition language (SV · EN / SV / EN)
            val voiceLangLabel = when (voiceLanguage) {
                VoiceLanguage.AUTO -> "SV · EN"
                VoiceLanguage.SWEDISH -> "SV"
                VoiceLanguage.ENGLISH -> "EN"
            }
            Surface(
                onClick = onToggleVoiceLanguage,
                shape = CircleShape,
                color = if (voiceLanguage != VoiceLanguage.AUTO) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
                contentColor = if (voiceLanguage != VoiceLanguage.AUTO) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.height(36.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = voiceLangLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            FilledTonalIconButton(
                onClick = onMicClick,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = if (isListening) {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                    contentColor = if (isListening) {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    }
                ),
                modifier = Modifier.scale(if (isListening) micScale else 1f)
            ) {
                Icon(
                    painter = AppIcons.Mic,
                    contentDescription = stringResource(
                        if (isListening) Res.string.cd_stop_listening else Res.string.cd_voice_input
                    )
                )
            }

            FilledIconButton(
                onClick = onSendClick,
                enabled = canSend
            ) {
                Icon(
                    painter = AppIcons.Send,
                    contentDescription = stringResource(Res.string.cd_send_message)
                )
            }
        }
    }
}

@Composable
private fun ChatMessageList(
    messages: List<ChatMessageUi>,
    isGenerating: Boolean,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        reverseLayout = true,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.Bottom)
    ) {
        if (isGenerating) {
            item(key = "typing_indicator") {
                TypingIndicatorBubble(modifier = Modifier.animateItem())
            }
        }
        items(
            items = messages.asReversed(),
            key = { it.id }
        ) { message ->
            val itemModifier = Modifier.animateItem()
            when (message) {
                is ChatMessageUi.User -> ChatMessageBubble(
                    text = message.text,
                    isFromUser = true,
                    modifier = itemModifier
                )
                is ChatMessageUi.Assistant -> ChatMessageBubble(
                    text = message.text,
                    isFromUser = false,
                    modifier = itemModifier
                )
                is ChatMessageUi.ToolCall -> ToolCallChip(
                    toolName = message.toolName,
                    arguments = message.arguments,
                    modifier = itemModifier
                )
                is ChatMessageUi.Error -> ChatErrorBubble(
                    text = stringResource(
                        when (message.type) {
                            ChatError.GENERATION_FAILED -> Res.string.chat_error_generic
                            ChatError.EMPTY_ANSWER -> Res.string.chat_error_empty_answer
                        }
                    ),
                    modifier = itemModifier
                )
            }
        }
    }
}

private val previewUpcoming = listOf(
    FlatBirthdayPerson(
        key = "person:Emma:1990-05-14",
        name = "Emma",
        birthday = LocalDate(1990, 5, 14),
        friendOf = "Simon",
        parentNames = emptyList(),
        childNames = listOf("Adam", "Bob"),
        partnerName = "Simon",
        ageNow = 35,
        turningAge = 36,
        nextBirthday = LocalDate(2026, 5, 14),
        daysUntilNextBirthday = 3
    )
)

private val previewMessages = listOf(
    ChatMessageUi.User(id = "1", text = "How old are Simon's kids?"),
    ChatMessageUi.ToolCall(id = "2", toolName = "search_people", arguments = "query=Simon"),
    ChatMessageUi.Assistant(
        id = "3",
        text = "Simon has two children:\n• Adam (8 years old, turns 9 on 2026-08-12)\n• Bob (5 years old, turns 6 on 2026-11-03)"
    )
)

@Preview(name = "Empty Greeting - Light")
@Composable
private fun BdaiChatScreenEmptyPreview() {
    AppTheme(darkTheme = false) {
        BdaiChatScreen(
            state = BdaiChatState(upcomingWithin7Days = previewUpcoming),
            onAction = {}
        )
    }
}

@Preview(name = "Active Chat - Dark")
@Composable
private fun BdaiChatScreenConversationDarkPreview() {
    AppTheme(darkTheme = true) {
        BdaiChatScreen(
            state = BdaiChatState(
                messages = previewMessages,
                upcomingWithin7Days = previewUpcoming,
                input = TextFieldState("Add Emma to the birthday calendar")
            ),
            onAction = {}
        )
    }
}

@Preview(name = "Model Setup")
@Composable
private fun BdaiChatScreenModelSetupPreview() {
    AppTheme(darkTheme = true) {
        BdaiChatScreen(
            state = BdaiChatState(modelState = ModelState.NotDownloaded),
            onAction = {}
        )
    }
}
