package se.oscarb.bdai.ui

import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import se.oscarb.bdai.agent.domain.AgentEvent
import se.oscarb.bdai.agent.domain.BirthdayAgentService
import se.oscarb.bdai.domain.BirthdayRepository
import se.oscarb.bdai.domain.SyncResult
import se.oscarb.bdai.domain.flattenBirthdays
import se.oscarb.bdai.notifications.BirthdayNotificationScheduler
import se.oscarb.bdai.notifications.currentLocalDate
import se.oscarb.bdai.voice.VoiceRecognizer
import se.oscarb.bdai.voice.VoiceState
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class BdaiViewModel(
    private val repository: BirthdayRepository,
    private val agentService: BirthdayAgentService,
    private val voiceRecognizer: VoiceRecognizer,
    private val notificationScheduler: BirthdayNotificationScheduler
) : ViewModel() {

    private val _state = MutableStateFlow(
        BdaiChatState(modelState = agentService.modelState.value)
    )
    val state = _state.asStateFlow()

    private var generationJob: Job? = null

    init {
        agentService.modelState
            .onEach { modelState ->
                _state.update { it.copy(modelState = modelState) }
            }
            .launchIn(viewModelScope)

        repository.entries
            .onEach { entries ->
                val today = currentLocalDate()
                val flat = entries.flattenBirthdays(today)
                val within7 = flat.filter { it.daysUntilNextBirthday in 0..7 }
                _state.update {
                    it.copy(
                        entries = entries,
                        flatBirthdays = flat,
                        upcomingWithin7Days = within7
                    )
                }
            }
            .launchIn(viewModelScope)

        repository.settings
            .onEach { settings ->
                _state.update { it.copy(settings = settings) }
                notificationScheduler.setNotificationsEnabled(settings.notificationsEnabled)
            }
            .launchIn(viewModelScope)

        voiceRecognizer.state
            .onEach { voiceState ->
                _state.update { it.copy(voiceState = voiceState) }
                when (voiceState) {
                    is VoiceState.PartialResult -> {
                        _state.value.input.setTextAndPlaceCursorAtEnd(voiceState.text)
                    }
                    is VoiceState.FinalResult -> {
                        _state.value.input.setTextAndPlaceCursorAtEnd(voiceState.text)
                        if (_state.value.isModelReady && !_state.value.isGenerating && voiceState.text.isNotBlank()) {
                            sendMessage(voiceState.text)
                        }
                    }
                    else -> Unit
                }
            }
            .launchIn(viewModelScope)
    }

    fun onAction(action: BdaiAction) {
        when (action) {
            BdaiAction.OnSendClick -> {
                val text = state.value.input.text.toString().trim()
                if (text.isNotEmpty()) {
                    sendMessage(text)
                }
            }
            is BdaiAction.OnSendPrompt -> {
                if (action.prompt.isNotBlank()) {
                    if (state.value.isModelReady && !state.value.isGenerating) {
                        sendMessage(action.prompt.trim())
                    } else {
                        state.value.input.setTextAndPlaceCursorAtEnd(action.prompt.trim())
                    }
                }
            }
            is BdaiAction.OnPrefillInput -> {
                state.value.input.setTextAndPlaceCursorAtEnd(action.text)
            }
            BdaiAction.OnStartVoice -> {
                voiceRecognizer.startListening()
            }
            BdaiAction.OnStopVoice -> {
                voiceRecognizer.stopListening()
            }
            BdaiAction.OnDownloadModelClick -> {
                agentService.startDownloadModel()
            }
            BdaiAction.OnRetryLoadModelClick -> {
                agentService.retryLoadModel()
            }
            BdaiAction.OnNewChatClick -> {
                startNewChat()
            }
            BdaiAction.OnOpenPeopleSheet -> {
                _state.update { it.copy(isPeopleSheetVisible = true) }
            }
            BdaiAction.OnDismissPeopleSheet -> {
                _state.update { it.copy(isPeopleSheetVisible = false) }
            }
            BdaiAction.OnOpenSettings -> {
                _state.update {
                    it.copy(
                        isSettingsVisible = true,
                        syncFeedbackCount = null,
                        syncErrorMessage = null
                    )
                }
            }
            BdaiAction.OnDismissSettings -> {
                _state.update { it.copy(isSettingsVisible = false) }
            }
            is BdaiAction.OnUpsertPerson -> {
                viewModelScope.launch {
                    repository.upsertPerson(
                        name = action.name.trim(),
                        birthday = action.birthday?.trim()?.takeIf { it.isNotEmpty() },
                        friendOf = action.friendOf?.trim()?.takeIf { it.isNotEmpty() },
                        partnerName = action.partnerName?.trim()?.takeIf { it.isNotEmpty() },
                        partnerBirthday = action.partnerBirthday?.trim()?.takeIf { it.isNotEmpty() }
                    )
                }
            }
            is BdaiAction.OnDeletePerson -> {
                viewModelScope.launch {
                    repository.deletePersonOrChild(
                        name = action.name,
                        birthdayHint = action.birthdayHint,
                        parentNameHint = action.parentNameHint
                    )
                }
            }
            is BdaiAction.OnUpdateNasUrl -> {
                viewModelScope.launch {
                    repository.updateSettings { it.copy(nasUrl = action.url.trim()) }
                }
            }
            is BdaiAction.OnToggleNotifications -> {
                viewModelScope.launch {
                    repository.updateSettings { it.copy(notificationsEnabled = action.enabled) }
                    notificationScheduler.setNotificationsEnabled(action.enabled)
                }
            }
            is BdaiAction.OnToggleNotify7Days -> {
                viewModelScope.launch {
                    repository.updateSettings { it.copy(notifyDaysBefore = action.enabled) }
                }
            }
            BdaiAction.OnTestNotificationsClick -> {
                viewModelScope.launch {
                    notificationScheduler.checkAndNotifyToday()
                }
            }
            BdaiAction.OnSyncWithNasClick -> {
                performNasSync(pullOnly = false)
            }
            BdaiAction.OnPullFromNasClick -> {
                performNasSync(pullOnly = true)
            }
            BdaiAction.OnClearSyncFeedback -> {
                _state.update { it.copy(syncFeedbackCount = null, syncErrorMessage = null) }
            }
        }
    }

    private fun performNasSync(pullOnly: Boolean) {
        if (_state.value.isSyncing) return
        _state.update {
            it.copy(
                isSyncing = true,
                syncFeedbackCount = null,
                syncErrorMessage = null
            )
        }
        viewModelScope.launch {
            val result = if (pullOnly) {
                repository.pullFromNas()
            } else {
                repository.syncWithNas()
            }
            when (result) {
                is SyncResult.Success -> {
                    _state.update {
                        it.copy(
                            isSyncing = false,
                            syncFeedbackCount = result.count,
                            syncErrorMessage = null
                        )
                    }
                }
                is SyncResult.Error -> {
                    _state.update {
                        it.copy(
                            isSyncing = false,
                            syncFeedbackCount = null,
                            syncErrorMessage = result.message
                        )
                    }
                }
            }
        }
    }

    private fun sendMessage(prompt: String) {
        val currentState = state.value
        if (currentState.isGenerating || !currentState.isModelReady) {
            return
        }
        currentState.input.clearText()
        _state.update {
            it.copy(
                messages = it.messages + ChatMessageUi.User(id = newId(), text = prompt),
                isGenerating = true
            )
        }
        generationJob = viewModelScope.launch {
            try {
                agentService.send(prompt).collect { event ->
                    appendMessage(event.toChatMessageUi())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                appendMessage(
                    ChatMessageUi.Error(
                        id = newId(),
                        type = ChatError.GENERATION_FAILED,
                        details = e.message
                    )
                )
            } finally {
                _state.update { it.copy(isGenerating = false) }
            }
        }
    }

    private fun startNewChat() {
        generationJob?.cancel()
        _state.update { it.copy(messages = emptyList(), isGenerating = false) }
        viewModelScope.launch { agentService.reset() }
    }

    private fun appendMessage(message: ChatMessageUi) {
        _state.update { it.copy(messages = it.messages + message) }
    }

    private fun AgentEvent.toChatMessageUi(): ChatMessageUi = when (this) {
        is AgentEvent.ToolCalled -> ChatMessageUi.ToolCall(
            id = newId(),
            toolName = call.name,
            arguments = call.arguments.values.entries
                .filter { it.value != null && it.value.toString().isNotBlank() }
                .joinToString(", ") { "${it.key}=${it.value}" }
        )
        is AgentEvent.Answer -> if (text.isBlank()) {
            ChatMessageUi.Error(id = newId(), type = ChatError.EMPTY_ANSWER)
        } else {
            ChatMessageUi.Assistant(id = newId(), text = text)
        }
    }

    private fun newId(): String = Uuid.random().toString()
}

val uiModule = module {
    viewModelOf(::BdaiViewModel)
}
