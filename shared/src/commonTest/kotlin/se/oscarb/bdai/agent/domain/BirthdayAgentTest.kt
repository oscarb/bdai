package se.oscarb.bdai.agent.domain

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import se.oscarb.bdai.agent.tools.provideAgentTools
import se.oscarb.bdai.data.DefaultBirthdayRepository
import se.oscarb.bdai.data.SampleBirthdays
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class BirthdayAgentTest {

    private val fixedClock = object : Clock {
        override fun now(): Instant = Instant.parse("2026-10-26T12:00:00Z")
    }
    private val utcZone = { TimeZone.UTC }

    @Test
    fun agentExecutesToolCallLoopAndAnswersWithExactAges() = runTest {
        val repository = DefaultBirthdayRepository(seedEntries = SampleBirthdays.defaultEntries())
        val tools = provideAgentTools(repository, clock = fixedClock, timeZone = utcZone)

        val fakeEngine = FakeLlmEngine { messages ->
            val lastMessage = messages.last()
            when (lastMessage) {
                is LlmMessage.User -> LlmResponse(
                    text = "",
                    toolCalls = listOf(
                        ToolCall(
                            name = "search_people",
                            arguments = ToolArguments(mapOf("query" to "Simons"))
                        )
                    )
                )
                is LlmMessage.ToolResults -> {
                    val toolResult = lastMessage.results.single()
                    assertEquals("search_people", toolResult.toolName)
                    val families = toolResult.result["families"] as List<*>
                    val simonFamily = families.single() as Map<*, *>
                    val children = simonFamily["children"] as List<*>
                    val childDescriptions = children.map { child ->
                        val map = child as Map<*, *>
                        "${map["name"]} är ${map["age_now"]} år (fyller ${map["turning_age"]})"
                    }
                    LlmResponse(
                        text = "Simons barn: ${childDescriptions.joinToString(", ")}.",
                        toolCalls = emptyList()
                    )
                }
            }
        }

        val agent = BirthdayAgent(
            engine = fakeEngine,
            tools = tools,
            clock = fixedClock,
            timeZone = utcZone
        )

        val events = agent.send("Hur gamla är Simons barn?").toList()
        assertEquals(2, events.size)

        val toolCalled = assertIs<AgentEvent.ToolCalled>(events[0])
        assertEquals("search_people", toolCalled.call.name)

        val answer = assertIs<AgentEvent.Answer>(events[1])
        assertEquals("Simons barn: Adam är 8 år (fyller 9), Elsa är 5 år (fyller 6).", answer.text)
        assertTrue(fakeEngine.lastSystemPrompt.orEmpty().contains("Swedish"))
    }

    @Test
    fun agentPassesDisambiguationToolExceptionBackToModel() = runTest {
        val repository = DefaultBirthdayRepository(seedEntries = SampleBirthdays.defaultEntries())
        val tools = provideAgentTools(repository, clock = fixedClock, timeZone = utcZone)

        val fakeEngine = FakeLlmEngine { messages ->
            when (val lastMessage = messages.last()) {
                is LlmMessage.User -> LlmResponse(
                    text = "",
                    toolCalls = listOf(
                        ToolCall(
                            name = "add_child",
                            arguments = ToolArguments(
                                mapOf(
                                    "child_name" to "Hugo",
                                    "child_birthday" to "idag",
                                    "parent_name" to "Alex"
                                )
                            )
                        )
                    )
                )
                is LlmMessage.ToolResults -> {
                    val error = lastMessage.results.single().result["error"]?.toString().orEmpty()
                    assertTrue(error.contains("Multiple parents named 'Alex'"))
                    LlmResponse(
                        text = "Det finns två Alex (födda 1984-03-12 med Leo och 1992-07-25 med Nora). Vilken Alex menar du?",
                        toolCalls = emptyList()
                    )
                }
            }
        }

        val agent = BirthdayAgent(
            engine = fakeEngine,
            tools = tools,
            clock = fixedClock,
            timeZone = utcZone
        )

        val events = agent.send("Alex fick en son idag som heter Hugo").toList()
        assertEquals(2, events.size)
        val answer = assertIs<AgentEvent.Answer>(events[1])
        assertTrue(answer.text.contains("Vilken Alex menar du?"))
    }
}

private class FakeLlmEngine(
    private val responder: (List<LlmMessage>) -> LlmResponse
) : LlmEngine {
    override var isLoaded: Boolean = true
    var lastSystemPrompt: String? = null

    override suspend fun load(modelPath: String, cacheDir: String) {
        isLoaded = true
    }

    override suspend fun createConversation(
        systemPrompt: String,
        tools: List<AgentTool>
    ): LlmConversation {
        lastSystemPrompt = systemPrompt
        return object : LlmConversation {
            private val history = mutableListOf<LlmMessage>()

            override suspend fun send(message: LlmMessage): LlmResponse {
                history += message
                return responder(history.toList())
            }

            override fun close() {
                history.clear()
            }
        }
    }
}
