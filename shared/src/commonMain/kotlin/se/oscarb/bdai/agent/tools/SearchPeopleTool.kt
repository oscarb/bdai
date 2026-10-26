package se.oscarb.bdai.agent.tools

import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import se.oscarb.bdai.agent.domain.AgentTool
import se.oscarb.bdai.agent.domain.ToolArguments
import se.oscarb.bdai.agent.domain.ToolException
import se.oscarb.bdai.agent.domain.ToolParameter
import se.oscarb.bdai.domain.BirthdayRepository
import se.oscarb.bdai.domain.flattenBirthdays
import kotlin.time.Clock

class SearchPeopleTool(
    private val repository: BirthdayRepository,
    private val clock: Clock = Clock.System,
    private val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() }
) : AgentTool {

    override val name = "search_people"

    override val description =
        "Searches people, families, partners, children, or friend_of by name or keyword. " +
            "Handles Swedish and English possessives (e.g. 'Simons' -> 'Simon', \"Alex's\" -> 'Alex'). " +
            "Returns matching family groups and flattened individuals with exact age_now, turning_age, " +
            "birthday, next_birthday, days_until_birthday, parents, children, and friend_of. " +
            "If multiple parents share the same name, all of them are returned separately with their respective children."

    override val parameters = listOf(
        ToolParameter(
            name = "query",
            type = ToolParameter.Type.STRING,
            description = "Person name, parent name, partner name, child name, or friend_of to search for (e.g. 'Simon', 'Simons', 'Alex', 'Adam')."
        )
    )

    override suspend fun execute(arguments: ToolArguments): Map<String, Any?> {
        val rawQuery = arguments.string("query")
            ?: throw ToolException("The 'query' parameter is required.")
        val today = clock.todayIn(timeZone())
        val candidates = extractSearchCandidates(rawQuery)

        val allEntries = repository.getEntriesSnapshot()
        val matchingEntries = allEntries.filter { entry ->
            matchesAnyCandidate(entry.name, candidates, rawQuery) ||
                matchesAnyCandidate(entry.partner?.name, candidates, rawQuery) ||
                matchesAnyCandidate(entry.friendOf, candidates, rawQuery) ||
                entry.children.any { child -> matchesAnyCandidate(child.name, candidates, rawQuery) }
        }

        val families = matchingEntries.mapIndexed { index, entry ->
            entry.toFamilyMap(today = today, familyIndex = index + 1)
        }

        val flattenedIndividuals = matchingEntries
            .flattenBirthdays(today)
            .map { it.toToolMap() }

        val duplicateParentNames = matchingEntries
            .groupingBy { it.name.lowercase() }
            .eachCount()
            .filter { it.value > 1 }
            .keys

        return buildMap {
            put("query", rawQuery)
            put("normalized_candidates", candidates)
            put("family_count", families.size)
            put("families", families)
            put("count", flattenedIndividuals.size)
            put("people", flattenedIndividuals)
            put("has_multiple_matches_with_same_name", duplicateParentNames.isNotEmpty())
            if (duplicateParentNames.isNotEmpty()) {
                put(
                    "disambiguation_note",
                    "Multiple families share the parent name(s): ${duplicateParentNames.joinToString()}. " +
                        "Check each family's birthday and children, and ask the user to clarify which one they mean if needed."
                )
            }
        }
    }
}
