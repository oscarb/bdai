package se.oscarb.bdai.agent.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

sealed interface ModelState {
    data object NotDownloaded : ModelState
    data class Downloading(val downloadedBytes: Long, val totalBytes: Long) : ModelState
    data class DownloadFailed(val message: String?) : ModelState
    data object Loading : ModelState
    data object Ready : ModelState
    data class LoadFailed(val message: String?) : ModelState
}

data class ToolCall(
    val name: String,
    val arguments: ToolArguments
)

sealed interface AgentEvent {
    data class ToolCalled(val call: ToolCall) : AgentEvent
    data class Answer(val text: String) : AgentEvent
}

interface BirthdayAgentService {
    val modelState: StateFlow<ModelState>
    fun send(prompt: String): Flow<AgentEvent>
    suspend fun reset()
    fun startDownloadModel()
    fun retryLoadModel()
}
