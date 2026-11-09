package se.oscarb.bdai.agent.tools

import kotlinx.datetime.LocalDate
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
        "Deletes one or more people, partners, or children by name. Supports optional birthday_hint or parent_name " +
            "to disambiguate when two people share the same name."

    override val parameters = listOf(
        ToolParameter(
            name = "name",
            type = ToolParameter.Type.STRING,
            description = "The name of the person or child to delete (or multiple names separated by ',' or 'och' / 'and')."
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
        val rawNameArg = arguments.string("name")
            ?: throw ToolException("The 'name' parameter is required.")
        val rawBirthdayHint = arguments.string("birthday_hint")
        val rawParentName = arguments.string("parent_name")?.trim()?.takeIf { it.isNotEmpty() }

        val today = clock.todayIn(timeZone())
        val normalizedBirthdayHint = rawBirthdayHint?.let { hint ->
            DateExpressions.parseBirthday(hint, today)?.toString() ?: hint.trim()
        }

        // If the user/model passed multiple names in a single call (e.g. "Anna och Erik" or "Anna, Erik"),
        // split and delete each one unless an exact single person has that literal compound name.
        val currentEntries = repository.getEntriesSnapshot()
        val hasExactLiteralMatch = currentEntries.any { entry ->
            entry.name.equals(rawNameArg.trim(), ignoreCase = true) ||
                entry.partner?.name.equals(rawNameArg.trim(), ignoreCase = true) ||
                entry.children.any { it.name.equals(rawNameArg.trim(), ignoreCase = true) }
        }

        val targetNames = if (hasExactLiteralMatch) {
            listOf(rawNameArg.trim())
        } else {
            rawNameArg
                .split(MULTI_NAME_SPLIT_REGEX)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .ifEmpty { listOf(rawNameArg.trim()) }
        }

        if (targetNames.size == 1) {
            val targetName = targetNames.single()
            deleteSingle(
                targetName = targetName,
                normalizedBirthdayHint = normalizedBirthdayHint,
                rawParentName = rawParentName,
                today = today
            )
            return mapOf(
                "status" to "deleted",
                "deleted_name" to targetName
            )
        }

        val deletedNames = mutableListOf<String>()
        val errors = mutableListOf<String>()
        for (targetName in targetNames) {
            try {
                deleteSingle(
                    targetName = targetName,
                    normalizedBirthdayHint = normalizedBirthdayHint,
                    rawParentName = rawParentName,
                    today = today
                )
                deletedNames += targetName
            } catch (e: ToolException) {
                errors += e.message ?: "Could not delete '$targetName'."
            }
        }

        if (deletedNames.isEmpty() && errors.isNotEmpty()) {
            throw ToolException(errors.joinToString(" "))
        }

        return buildMap {
            put("status", "deleted")
            put("deleted_name", deletedNames.joinToString(", "))
            put("deleted_names", deletedNames)
            if (errors.isNotEmpty()) {
                put("warnings", errors)
            }
        }
    }

    private suspend fun deleteSingle(
        targetName: String,
        normalizedBirthdayHint: String?,
        rawParentName: String?,
        today: LocalDate
    ) {
        val strippedParentName = rawParentName?.let { stripPossessive(it).ifEmpty { it } }
        val entries = repository.getEntriesSnapshot()
        val matches = mutableListOf<String>()
        var matchedActualParentName: String? = rawParentName

        for (entry in entries) {
            val parentMatchesHint = rawParentName == null ||
                entry.name.equals(rawParentName, ignoreCase = true) ||
                entry.name.equals(strippedParentName, ignoreCase = true) ||
                entry.partner?.name?.equals(rawParentName, ignoreCase = true) == true ||
                entry.partner?.name?.equals(strippedParentName, ignoreCase = true) == true

            if (rawParentName == null && entry.name.equals(targetName, ignoreCase = true)) {
                val bdayMatches = normalizedBirthdayHint == null ||
                    entry.birthday == normalizedBirthdayHint ||
                    entry.birthday?.startsWith(normalizedBirthdayHint) == true
                if (bdayMatches) {
                    val kids = entry.children.joinToString { it.name }.ifEmpty { "none" }
                    matches += "${entry.name} (birthday: ${entry.birthday ?: "unknown"}, children: $kids)"
                }
            }

            val partner = entry.partner
            if (rawParentName == null && partner != null && partner.name.equals(targetName, ignoreCase = true)) {
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
                            matchedActualParentName = entry.name
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
            parentNameHint = if (rawParentName != null) matchedActualParentName else null
        )

        if (!deleted) {
            throw ToolException("Could not delete '$targetName'.")
        }
    }

    private companion object {
        val MULTI_NAME_SPLIT_REGEX = Regex("""\s*(?:,|&|\boch\b|\band\b)\s*""", RegexOption.IGNORE_CASE)
    }
}
