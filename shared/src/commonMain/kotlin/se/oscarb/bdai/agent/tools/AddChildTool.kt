package se.oscarb.bdai.agent.tools

import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import se.oscarb.bdai.agent.domain.AgentTool
import se.oscarb.bdai.agent.domain.ToolArguments
import se.oscarb.bdai.agent.domain.ToolException
import se.oscarb.bdai.agent.domain.ToolParameter
import se.oscarb.bdai.domain.BirthdayRepository
import se.oscarb.bdai.domain.PersonEntry
import kotlin.time.Clock

class AddChildTool(
    private val repository: BirthdayRepository,
    private val clock: Clock = Clock.System,
    private val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() }
) : AgentTool {

    override val name = "add_child"

    override val description =
        "Adds or updates a child under a parent. If multiple parents in the list have the same name " +
            "and parent_birthday_hint is not provided, returns a disambiguation error listing each parent's " +
            "birthday and existing children so you can ask the user which parent they mean."

    override val parameters = listOf(
        ToolParameter(
            name = "child_name",
            type = ToolParameter.Type.STRING,
            description = "The child's name."
        ),
        ToolParameter(
            name = "child_birthday",
            type = ToolParameter.Type.STRING,
            description = "The child's birthday or natural date/age phrase (e.g. '26 oktober', 'hon fyllde 1 den 26 oktober', '2025-10-26', 'idag'). Prefer passing the user's natural wording instead of doing mental year math."
        ),
        ToolParameter(
            name = "parent_name",
            type = ToolParameter.Type.STRING,
            description = "The name of the parent (e.g. 'Simon', 'Alex')."
        ),
        ToolParameter(
            name = "age",
            type = ToolParameter.Type.INT,
            description = "Optional age in years if the user mentioned how old the child is or what age they turned (e.g. 'hon fyllde 1 den 26 oktober' -> age=1). Pass age here so the app subtracts the right number of years.",
            isRequired = false
        ),
        ToolParameter(
            name = "parent_birthday_hint",
            type = ToolParameter.Type.STRING,
            description = "Optional birthday, birth year, or existing child name of the parent to disambiguate when two parents share the same name.",
            isRequired = false
        ),
        ToolParameter(
            name = "friend_of",
            type = ToolParameter.Type.STRING,
            description = "Optional friend_of value if a new parent entry is created.",
            isRequired = false
        )
    )

    override suspend fun execute(arguments: ToolArguments): Map<String, Any?> {
        val childName = arguments.string("child_name")
            ?: throw ToolException("The 'child_name' parameter is required.")
        val rawChildBirthday = arguments.string("child_birthday")
            ?: throw ToolException("The 'child_birthday' parameter is required.")
        val rawParentName = arguments.string("parent_name")
            ?: throw ToolException("The 'parent_name' parameter is required.")
        val ageHint = arguments.int("age")
            ?: arguments.int("child_age")
            ?: arguments.string("age")?.toIntOrNull()
        val rawHint = arguments.string("parent_birthday_hint")
        val friendOf = arguments.string("friend_of")

        val today = clock.todayIn(timeZone())
        val allEntries = repository.getEntriesSnapshot()
        val existingChildBirthday = findExistingBirthday(allEntries, childName, parentNameHint = rawParentName)
            ?: findExistingBirthday(allEntries, childName)
        val childBirthday = DateExpressions.parseBirthday(
            input = rawChildBirthday,
            today = today,
            ageHint = ageHint,
            existingBirthday = existingChildBirthday
        ) ?: throw ToolException(unknownDateMessage(rawChildBirthday))

        val matchingParents = findMatchingParents(allEntries, rawParentName)

        var resolvedHint = rawHint?.let { hint ->
            DateExpressions.parseBirthday(hint, today)?.toString() ?: hint.trim()
        }

        if (matchingParents.size > 1) {
            if (resolvedHint.isNullOrBlank()) {
                throw ToolException(buildDisambiguationMessage(rawParentName, matchingParents))
            }
            val narrowed = matchingParents.filter { entry ->
                matchesParentHint(entry, resolvedHint)
            }
            if (narrowed.size != 1) {
                throw ToolException(buildDisambiguationMessage(rawParentName, matchingParents))
            }
            resolvedHint = narrowed.single().birthday ?: resolvedHint
        }

        val effectiveParentName = if (matchingParents.size == 1) {
            matchingParents.single().name
        } else {
            stripApostrophePossessive(rawParentName).ifEmpty { rawParentName.trim() }
        }

        val updatedParent = repository.addOrUpdateChild(
            parentName = effectiveParentName,
            parentBirthdayHint = resolvedHint,
            childName = childName,
            childBirthday = childBirthday.toString(),
            friendOfIfNewParent = friendOf
        )

        val familyMap = updatedParent.toFamilyMap(today)
        val savedChildMap = (familyMap["children"] as? List<*>)
            ?.mapNotNull { it as? Map<*, *> }
            ?.firstOrNull { (it["name"] as? String).equals(childName.trim(), ignoreCase = true) }

        return buildMap {
            put("status", "saved")
            put("child_name", childName.trim())
            put("child_birthday", childBirthday.toString())
            savedChildMap?.get("info_sv")?.let { put("summary_sv", it) }
            savedChildMap?.get("info_en")?.let { put("summary_en", it) }
            put("family", familyMap)
        }
    }

    private fun findMatchingParents(entries: List<PersonEntry>, rawParentName: String): List<PersonEntry> {
        val exact = rawParentName.trim()
        val stripped = stripPossessive(rawParentName)
        return entries.filter { entry ->
            entry.name.equals(exact, ignoreCase = true) ||
                entry.name.equals(stripped, ignoreCase = true) ||
                entry.partner?.name?.equals(exact, ignoreCase = true) == true ||
                entry.partner?.name?.equals(stripped, ignoreCase = true) == true
        }
    }

    private fun matchesParentHint(entry: PersonEntry, hint: String): Boolean {
        val normalized = hint.trim()
        return entry.birthday.equals(normalized, ignoreCase = true) ||
            entry.birthday?.startsWith(normalized) == true ||
            entry.partner?.birthday.equals(normalized, ignoreCase = true) ||
            entry.partner?.birthday?.startsWith(normalized) == true ||
            entry.partner?.name.equals(normalized, ignoreCase = true) ||
            entry.children.any { it.name.equals(normalized, ignoreCase = true) }
    }

    private fun buildDisambiguationMessage(rawParentName: String, parents: List<PersonEntry>): String {
        val cleanName = stripPossessive(rawParentName).ifEmpty { rawParentName.trim() }
        val details = parents.mapIndexed { index, parent ->
            val bday = parent.birthday ?: "unknown birthday"
            val partnerInfo = parent.partner?.let { ", partner: ${it.name}" }.orEmpty()
            val childrenInfo = if (parent.children.isEmpty()) {
                "no children listed"
            } else {
                "children: ${parent.children.joinToString { "${it.name} (${it.birthday})" }}"
            }
            "${index + 1}) ${parent.name} (birthday: $bday$partnerInfo, $childrenInfo)"
        }.joinToString("; ")

        return "Multiple parents named '$cleanName' were found: $details. " +
            "Please ask the user which '$cleanName' they mean and call add_child again with parent_birthday_hint."
    }
}
