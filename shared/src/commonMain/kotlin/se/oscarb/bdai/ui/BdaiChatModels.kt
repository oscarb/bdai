package se.oscarb.bdai.ui

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Stable
import se.oscarb.bdai.agent.domain.ModelState
import se.oscarb.bdai.domain.BdaiSettings
import se.oscarb.bdai.domain.FlatBirthdayPerson
import se.oscarb.bdai.domain.PersonEntry
import se.oscarb.bdai.voice.VoiceState

sealed interface ChatMessageUi {
    val id: String

    data class User(override val id: String, val text: String) : ChatMessageUi
    data class Assistant(override val id: String, val text: String) : ChatMessageUi
    data class ToolCall(override val id: String, val toolName: String, val arguments: String) : ChatMessageUi
    data class Error(override val id: String, val type: ChatError, val details: String? = null) : ChatMessageUi
}

enum class ChatError {
    GENERATION_FAILED,
    EMPTY_ANSWER
}

@Stable
data class BdaiChatState(
    val messages: List<ChatMessageUi> = emptyList(),
    val input: TextFieldState = TextFieldState(),
    val isGenerating: Boolean = false,
    val modelState: ModelState = ModelState.Ready,
    val voiceState: VoiceState = VoiceState.Idle,
    val entries: List<PersonEntry> = emptyList(),
    val flatBirthdays: List<FlatBirthdayPerson> = emptyList(),
    val upcomingWithin7Days: List<FlatBirthdayPerson> = emptyList(),
    val settings: BdaiSettings = BdaiSettings(),
    val isPeopleSheetVisible: Boolean = false,
    val isSettingsVisible: Boolean = false,
    val isSyncing: Boolean = false,
    val syncFeedbackCount: Int? = null,
    val syncErrorMessage: String? = null
) {
    val isModelReady: Boolean get() = modelState == ModelState.Ready
    val isListening: Boolean get() = voiceState is VoiceState.Listening || voiceState is VoiceState.PartialResult
    val canSend: Boolean get() = input.text.isNotBlank() && !isGenerating && isModelReady
    val upcomingBadgeCount: Int get() = upcomingWithin7Days.size
}

sealed interface BdaiAction {
    data object OnSendClick : BdaiAction
    data class OnSendPrompt(val prompt: String) : BdaiAction
    data class OnPrefillInput(val text: String) : BdaiAction
    data object OnStartVoice : BdaiAction
    data object OnStopVoice : BdaiAction
    data object OnDownloadModelClick : BdaiAction
    data object OnRetryLoadModelClick : BdaiAction
    data object OnNewChatClick : BdaiAction
    data object OnOpenPeopleSheet : BdaiAction
    data object OnDismissPeopleSheet : BdaiAction
    data object OnOpenSettings : BdaiAction
    data object OnDismissSettings : BdaiAction
    data class OnUpsertPerson(
        val name: String,
        val birthday: String?,
        val friendOf: String?,
        val partnerName: String? = null,
        val partnerBirthday: String? = null
    ) : BdaiAction
    data class OnDeletePerson(
        val name: String,
        val birthdayHint: String? = null,
        val parentNameHint: String? = null
    ) : BdaiAction
    data class OnUpdateNasUrl(val url: String) : BdaiAction
    data class OnToggleNotifications(val enabled: Boolean) : BdaiAction
    data class OnToggleNotify7Days(val enabled: Boolean) : BdaiAction
    data object OnTestNotificationsClick : BdaiAction
    data object OnSyncWithNasClick : BdaiAction
    data object OnPullFromNasClick : BdaiAction
    data object OnClearSyncFeedback : BdaiAction
}
