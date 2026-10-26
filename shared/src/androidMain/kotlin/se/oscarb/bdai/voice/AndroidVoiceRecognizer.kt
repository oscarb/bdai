package se.oscarb.bdai.voice

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import java.util.Locale

/**
 * Bridge connected in MainActivity to handle runtime permission results via ActivityResultLauncher.
 */
object AndroidPermissionBridge {
    var requestPermissionHandler: ((permission: String, onResult: (Boolean) -> Unit) -> Unit)? = null
}

@Composable
actual fun rememberMicPermissionRequester(onResult: (Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    return remember(context, onResult) {
        {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            if (granted) {
                onResult(true)
            } else {
                val handler = AndroidPermissionBridge.requestPermissionHandler
                if (handler != null) {
                    handler(Manifest.permission.RECORD_AUDIO, onResult)
                } else {
                    (context as? Activity)?.let { activity ->
                        ActivityCompat.requestPermissions(
                            activity,
                            arrayOf(Manifest.permission.RECORD_AUDIO),
                            1001
                        )
                    }
                    onResult(false)
                }
            }
        }
    }
}

class AndroidVoiceRecognizer(
    private val context: Context
) : VoiceRecognizer {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow<VoiceState>(VoiceState.Idle)
    override val state: StateFlow<VoiceState> = _state.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _state.value = VoiceState.Listening
        }

        override fun onBeginningOfSpeech() {
            _state.value = VoiceState.Listening
        }

        override fun onRmsChanged(rmsdB: Float) = Unit

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() = Unit

        override fun onError(error: Int) {
            if (error == SpeechRecognizer.ERROR_CLIENT && _state.value == VoiceState.Idle) {
                return
            }
            val message = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timed out"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network error during speech recognition"
                else -> "Voice input error ($error)"
            }
            _state.value = VoiceState.Error(message)
        }

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            _state.value = if (text.isNotEmpty()) {
                VoiceState.FinalResult(text)
            } else {
                VoiceState.Idle
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            if (text.isNotEmpty()) {
                _state.value = VoiceState.PartialResult(text)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    override fun startListening(languageTag: String?) {
        mainHandler.post {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                _state.value = VoiceState.Error("Speech recognition is not available on this device")
                return@post
            }
            try {
                val recognizer = speechRecognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also {
                    it.setRecognitionListener(listener)
                    speechRecognizer = it
                }
                val localeTag = languageTag?.takeIf { it.isNotBlank() } ?: Locale.getDefault().toLanguageTag()
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, localeTag)
                }
                _state.value = VoiceState.Listening
                recognizer.startListening(intent)
            } catch (e: Exception) {
                _state.value = VoiceState.Error(e.message ?: "Failed to start voice input")
            }
        }
    }

    override fun stopListening() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
            } catch (_: Exception) {
            }
            if (_state.value is VoiceState.Listening) {
                _state.value = VoiceState.Idle
            }
        }
    }
}

val voicePlatformModule = module {
    single<VoiceRecognizer> { AndroidVoiceRecognizer(androidContext()) }
}
