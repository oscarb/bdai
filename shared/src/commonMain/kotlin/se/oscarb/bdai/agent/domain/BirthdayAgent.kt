package se.oscarb.bdai.agent.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import se.oscarb.bdai.agent.data.ModelManager
import kotlin.time.Clock

/**
 * Runs the on-device birthday agent loop: sends the user's prompt, executes any tool calls the
 * model requests, feeds the tool results back, and repeats until the model responds with text.
 */
class BirthdayAgent(
    private val engine: LlmEngine,
    tools: List<AgentTool>,
    private val clock: Clock = Clock.System,
    private val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() }
) {
    private val toolsByName = tools.associateBy { it.name }
    private val mutex = Mutex()

    private var conversation: LlmConversation? = null
    private var conversationDate: LocalDate? = null

    fun send(prompt: String): Flow<AgentEvent> = flow {
        mutex.withLock {
            val conversation = currentConversation()
            try {
                var response = conversation.send(LlmMessage.User(prompt))
                var round = 0
                while (response.toolCalls.isNotEmpty() && round < MAX_TOOL_ROUNDS) {
                    round++
                    val results = response.toolCalls.map { call ->
                        emit(AgentEvent.ToolCalled(call))
                        ToolResult(toolName = call.name, result = execute(call))
                    }
                    response = conversation.send(LlmMessage.ToolResults(results))
                }
                emit(AgentEvent.Answer(response.text.trim()))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A failed turn can leave the native conversation in a broken state.
                closeConversation()
                throw e
            }
        }
    }

    suspend fun reset() {
        mutex.withLock { closeConversation() }
    }

    private suspend fun currentConversation(): LlmConversation {
        val today = clock.todayIn(timeZone())
        if (conversationDate != today) {
            closeConversation()
        }
        return conversation ?: engine.createConversation(
            systemPrompt = buildSystemPrompt(today),
            tools = toolsByName.values.toList()
        ).also {
            conversation = it
            conversationDate = today
        }
    }

    private suspend fun execute(call: ToolCall): Map<String, Any?> {
        val tool = toolsByName[call.name]
            ?: return errorResult("Unknown tool '${call.name}'. Available tools: ${toolsByName.keys.joinToString()}.")
        return try {
            tool.execute(call.arguments)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errorResult(e.message ?: "The tool failed.")
        }
    }

    private fun errorResult(message: String): Map<String, Any?> = mapOf("error" to message)

    private fun closeConversation() {
        conversation?.close()
        conversation = null
        conversationDate = null
    }

    internal fun buildSystemPrompt(today: LocalDate): String = """
        You are a helpful birthday and family assistant inside the bdai app. You can look up and update people, partners, children, and birthdays only through the provided tools.
        Today is ${today.toSwedishDate()} ($today).

        Rules:
        - Language (Swedish & English): You understand both Swedish and English seamlessly. Always reply in the SAME language the user used (if the user writes in Swedish, reply in natural Swedish; if the user writes in English, reply in English).
        - Tool-first: For any question or action about people, ages, birthdays, children, partners, or upcoming birthdays, ALWAYS call a tool first. Never guess or make up birthdays or ages.
        - Multiple people in one request: If the user asks to remove, add, update, or look up multiple people at once (e.g. "Ta bort Anna och Erik"), make sure to call the tool for EVERY person mentioned before giving your final answer.
        - Possessives & family questions: For questions like "Hur gamla är Simons barn?", "När fyller Simons barn år?", or "How old are Simon's kids?", call `search_people` with the name ("Simon" or "Simons").
        - Answering birthday & age questions:
          * Every person in tool results has a pre-computed `info_sv` summary sentence with exact age, birth date, turning age, and days until next birthday. Copy numbers and dates faithfully from the tool result without doing mental math.
          * When asked who has the NEXT birthday (singular, e.g. "Vem fyller år härnäst?", "Whose birthday is next?"), answer with `next_birthday_sv` (only the very next birthday date, not the entire 30-day list).
          * When asked "Och efter det?" / "And after that?", answer with the next birthday(s) in `birthdays` that come after the one you just mentioned.
        - Adding people or children with an age: When the user mentions an age while adding or updating a birthday (for example "Lägg till Saga, hon fyllde 1 den 26 oktober"), pass `age` (e.g. `age=1`) and pass the natural date phrase in `birthday` / `child_birthday` (e.g. `"26 oktober"`).
        - Disambiguation: Only ask the user which person they mean when `has_multiple_matches_with_same_name` is `true` or when a tool returns a disambiguation error.
        - Formatting: Keep answers concise and write plain text without Markdown formatting. When listing multiple items, put each one on its own line starting with "• ".
    """.trimIndent()

    private companion object {
        const val MAX_TOOL_ROUNDS = 5
    }
}

class BirthdayAgentServiceImpl(
    private val agent: BirthdayAgent,
    private val modelManager: ModelManager
) : BirthdayAgentService {

    constructor(
        engine: LlmEngine,
        tools: List<AgentTool>,
        modelManager: ModelManager
    ) : this(
        agent = BirthdayAgent(engine = engine, tools = tools),
        modelManager = modelManager
    )

    init {
        modelManager.prepare()
    }

    override val modelState: StateFlow<ModelState>
        get() = modelManager.state

    override fun send(prompt: String): Flow<AgentEvent> {
        modelManager.prepare()
        return agent.send(prompt)
    }

    override suspend fun reset() {
        agent.reset()
    }

    override fun startDownloadModel() {
        modelManager.download()
    }

    override fun retryLoadModel() {
        modelManager.retryLoad()
    }
}
