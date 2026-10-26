package se.oscarb.bdai.agent.domain

sealed interface LlmMessage {
    data class User(val text: String) : LlmMessage
    data class ToolResults(val results: List<ToolResult>) : LlmMessage
}

data class ToolResult(
    val toolName: String,
    val result: Map<String, Any?>
)

data class LlmResponse(
    val text: String,
    val toolCalls: List<ToolCall>
)
