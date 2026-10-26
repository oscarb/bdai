package se.oscarb.bdai.agent.tools

import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import se.oscarb.bdai.agent.domain.AgentTool
import se.oscarb.bdai.agent.domain.ToolArguments
import se.oscarb.bdai.agent.domain.ToolException
import se.oscarb.bdai.agent.domain.ToolParameter
import se.oscarb.bdai.domain.BirthdayRepository
import kotlin.time.Clock

class AddOrUpdatePersonTool(
    private val repository: BirthdayRepository,
    private val clock: Clock = Clock.System,
    private val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() }
) : AgentTool {

    override val name = "add_or_update_person"

    override val description =
        "Adds a new top-level person or updates an existing person's birthday, friend_of tag, or partner details."

    override val parameters = listOf(
        ToolParameter(
            name = "name",
            type = ToolParameter.Type.STRING,
            description = "The person's name."
        ),
        ToolParameter(
            name = "birthday",
            type = ToolParameter.Type.STRING,
            description = "Optional birthday in YYYY-MM-DD format or natural date expression (e.g. ${DateExpressions.DATE_EXAMPLES}).",
            isRequired = false
        ),
        ToolParameter(
            name = "friend_of",
            type = ToolParameter.Type.STRING,
            description = "Optional tag indicating whose friend this person is (e.g. 'Me' or 'Partner').",
            isRequired = false
        ),
        ToolParameter(
            name = "partner_name",
            type = ToolParameter.Type.STRING,
            description = "Optional name of the person's partner.",
            isRequired = false
        ),
        ToolParameter(
            name = "partner_birthday",
            type = ToolParameter.Type.STRING,
            description = "Optional partner birthday in YYYY-MM-DD format or natural date expression.",
            isRequired = false
        )
    )

    override suspend fun execute(arguments: ToolArguments): Map<String, Any?> {
        val name = arguments.string("name")
            ?: throw ToolException("The 'name' parameter is required.")
        val today = clock.todayIn(timeZone())

        val parsedBirthday = arguments.string("birthday")?.let { raw ->
            DateExpressions.parseBirthday(raw, today)
                ?: throw ToolException(unknownDateMessage(raw))
        }
        val parsedPartnerBirthday = arguments.string("partner_birthday")?.let { raw ->
            DateExpressions.parseBirthday(raw, today)
                ?: throw ToolException(unknownDateMessage(raw))
        }

        val savedEntry = repository.upsertPerson(
            name = name,
            birthday = parsedBirthday?.toString(),
            friendOf = arguments.string("friend_of"),
            partnerName = arguments.string("partner_name"),
            partnerBirthday = parsedPartnerBirthday?.toString()
        )

        return mapOf(
            "status" to "saved",
            "person" to savedEntry.toFamilyMap(today)
        )
    }
}
