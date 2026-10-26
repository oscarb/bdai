package se.oscarb.bdai.voice

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.StateFlow

sealed interface VoiceState {
    data object Idle : VoiceState
    data object Listening : VoiceState
    data class PartialResult(val text: String) : VoiceState
    data class FinalResult(val text: String) : VoiceState
    data class Error(val message: String) : VoiceState
}

interface VoiceRecognizer {
    val state: StateFlow<VoiceState>
    fun startListening(languageTag: String? = null)
    fun stopListening()
}

/**
 * Returns a lambda that checks/requests RECORD_AUDIO permission at runtime and invokes [onResult].
 */
@Composable
expect fun rememberMicPermissionRequester(onResult: (Boolean) -> Unit): () -> Unit
