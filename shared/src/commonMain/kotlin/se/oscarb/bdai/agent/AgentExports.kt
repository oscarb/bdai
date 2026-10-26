package se.oscarb.bdai.agent

typealias LocalModel = se.oscarb.bdai.agent.data.LocalModel
typealias ModelPaths = se.oscarb.bdai.agent.data.ModelPaths
typealias DownloadProgress = se.oscarb.bdai.agent.data.DownloadProgress
typealias ModelDownloader = se.oscarb.bdai.agent.data.ModelDownloader
typealias ModelDownloadException = se.oscarb.bdai.agent.data.ModelDownloadException
typealias ModelManager = se.oscarb.bdai.agent.data.ModelManager

typealias AgentTool = se.oscarb.bdai.agent.domain.AgentTool
typealias ToolParameter = se.oscarb.bdai.agent.domain.ToolParameter
typealias ToolException = se.oscarb.bdai.agent.domain.ToolException
typealias LlmEngine = se.oscarb.bdai.agent.domain.LlmEngine
typealias LlmConversation = se.oscarb.bdai.agent.domain.LlmConversation
typealias LlmMessage = se.oscarb.bdai.agent.domain.LlmMessage
typealias ToolResult = se.oscarb.bdai.agent.domain.ToolResult
typealias LlmResponse = se.oscarb.bdai.agent.domain.LlmResponse
typealias BirthdayAgent = se.oscarb.bdai.agent.domain.BirthdayAgent
typealias BirthdayAgentServiceImpl = se.oscarb.bdai.agent.domain.BirthdayAgentServiceImpl

fun provideAgentTools(
    repository: se.oscarb.bdai.domain.BirthdayRepository
): List<AgentTool> = se.oscarb.bdai.agent.tools.provideAgentTools(repository)

