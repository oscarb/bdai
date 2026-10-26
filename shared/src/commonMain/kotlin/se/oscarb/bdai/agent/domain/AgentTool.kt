package se.oscarb.bdai.agent.domain

/**
 * A function the model can call. The result is a map of plain values (strings, numbers, booleans,
 * lists and maps), which the [LlmEngine] passes back to the model.
 */
interface AgentTool {
    val name: String
    val description: String
    val parameters: List<ToolParameter>

    suspend fun execute(arguments: ToolArguments): Map<String, Any?>
}

data class ToolParameter(
    val name: String,
    val type: Type,
    val description: String,
    val isRequired: Boolean = true
) {
    enum class Type {
        STRING,
        INT,
        BOOLEAN
    }
}

/**
 * Thrown by a tool when a call can't be completed (e.g. missing argument, unknown date, or
 * multiple people with the same name requiring disambiguation). The message is passed back to
 * the model so it can correct the call or ask the user.
 */
class ToolException(message: String) : Exception(message)
