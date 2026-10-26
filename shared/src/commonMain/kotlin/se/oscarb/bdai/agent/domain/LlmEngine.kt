package se.oscarb.bdai.agent.domain

/**
 * Platform-specific runtime that runs a local LLM on top of LiteRT-LM.
 * Tool execution lives in [BirthdayAgent].
 */
interface LlmEngine {
    val isLoaded: Boolean

    suspend fun load(modelPath: String, cacheDir: String)

    suspend fun createConversation(systemPrompt: String, tools: List<AgentTool>): LlmConversation
}
