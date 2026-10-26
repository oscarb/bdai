package se.oscarb.bdai.agent.tools

import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import se.oscarb.bdai.agent.domain.AgentTool
import se.oscarb.bdai.agent.domain.ToolArguments
import se.oscarb.bdai.agent.domain.ToolException
import se.oscarb.bdai.agent.domain.ToolParameter
import se.oscarb.bdai.domain.BirthdayRepository
import kotlin.time.Clock

class DeletePersonTool(
    private val repository: BirthdayRepository,
    private val clock: Clock = Clock.System,
    private val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() }
) : AgentTool {

    override val name = "delete_person"

    override val description =
        "Deletes a person, partner, or child by name. Supports optional birthday_hint or parent_name " +
            "to disambiguate when two people share the same name."

    override val parameters = listOf(
        ToolParameter(
            name = "name",
            type = ToolParameter.Type.STRING,
            description = "The name of the person or child to delete."
        ),
        ToolParameter(
            name = "birthday_hint",
            type = ToolParameter.Type.STRING,
            description = "Optional birthday or birth year to disambiguate if multiple people share the same name.",
            isRequired = false
        ),
        ToolParameter(
            name = "parent_name",
            type = ToolParameter.Type.STRING,
            description = "Optional parent name if deleting a specific child.",
            isRequired = false
        )
    )

    override suspend fun execute(arguments: ToolArguments): Map<String, Any?> {
        val targetName = arguments.string("name")
            ?: throw ToolException("The 'name' parameter is required.")
        val rawBirthdayHint = arguments.string("birthday_hint")
        val parentNameHint = arguments.string("parent_name")?.let { stripPossessive(it).ifEmpty { it.trim() } }

        val today = clock.todayIn(timeZone())
        val normalizedBirthdayHint = rawBirthdayHint?.let { hint ->
            DateExpressions.parseBirthday(hint, today)?.toString() ?: hint.trim()
        }

        val entries = repository.getEntriesSnapshot()
        val matches = mutableListOf<String>()

        for (entry in entries) {
            val parentMatchesHint = parentNameHint == null ||
                entry.name.equals(parentNameHint, ignoreCase = true) ||
                entry.partner?.name?.equals(parentNameHint, ignoreCase = true) == true

            if (parentNameHint == null && entry.name.equals(targetName, ignoreCase = true)) {
                val bdayMatches = normalizedBirthdayHint == null ||
                    entry.birthday == normalizedBirthdayHint ||
                    entry.birthday?.startsWith(normalizedBirthdayHint) == true
                if (bdayMatches) {
                    val kids = entry.children.joinToString { it.name }.ifEmpty { "none" }
                    matches += "${entry.name} (birthday: ${entry.birthday ?: "unknown"}, children: $kids)"
                }
            }

            val partner = entry.partner
            if (parentNameHint == null && partner != null && partner.name.equals(targetName, ignoreCase = true)) {
                val bdayMatches = normalizedBirthdayHint == null ||
                    partner.birthday == normalizedBirthdayHint ||
                    partner.birthday?.startsWith(normalizedBirthdayHint) == true
                if (bdayMatches) {
                    matches += "${partner.name} (partner of ${entry.name}, birthday: ${partner.birthday ?: "unknown"})"
                }
            }

            if (parentMatchesHint) {
                for (child in entry.children) {
                    if (child.name.equals(targetName, ignoreCase = true)) {
                        val bdayMatches = normalizedBirthdayHint == null ||
                            child.birthday == normalizedBirthdayHint ||
                            child.birthday.startsWith(normalizedBirthdayHint)
                        if (bdayMatches) {
                            matches += "${child.name} (child of ${entry.name}, birthday: ${child.birthday})"
                        }
                    }
                }
            }
        }

        if (matches.isEmpty()) {
            throw ToolException("No person or child named '$targetName' was found.")
        }

        if (matches.size > 1) {
            throw ToolException(
                "Multiple people named '$targetName' were found: ${matches.joinToString("; ")}. " +
                    "Please ask the user which one they want to delete and provide birthday_hint or parent_name."
            )
        }

        val deleted = repository.deletePersonOrChild(
            name = targetName,
            birthdayHint = normalizedBirthdayHint,
            parentNameHint = parentNameHint
        )

        if (!deleted) {
            throw ToolException("Could not delete '$targetName'.")
        }

        return mapOf(
            "status" to "deleted",
            "deleted_name" to targetName
        )
    }
}
