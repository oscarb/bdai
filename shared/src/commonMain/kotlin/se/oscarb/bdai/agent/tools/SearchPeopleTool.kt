package se.oscarb.bdai.agent.tools

import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import se.oscarb.bdai.agent.domain.AgentTool
import se.oscarb.bdai.agent.domain.ToolArguments
import se.oscarb.bdai.agent.domain.ToolException
import se.oscarb.bdai.agent.domain.ToolParameter
import se.oscarb.bdai.domain.BirthdayRepository
import se.oscarb.bdai.domain.FlatBirthdayPerson
import se.oscarb.bdai.domain.PersonEntry
import se.oscarb.bdai.domain.flattenBirthdays
import se.oscarb.bdai.domain.toLocalDateOrNull
import kotlin.time.Clock

class SearchPeopleTool(
    private val repository: BirthdayRepository,
    private val clock: Clock = Clock.System,
    private val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() }
) : AgentTool {

    override val name = "search_people"

    override val description =
        "Searches people, families, partners, children, friend_of, birth month, or birth year by name or keyword. " +
            "Handles Swedish and English possessives (e.g. 'Simons' -> 'Simon', \"Alex's\" -> 'Alex'). " +
            "Returns matching families with pre-formatted 'info_sv' and 'info_en' sentences, exact age_now, " +
            "turning_age, birthday, days_until_birthday, partner, and children. " +
            "Check 'has_multiple_matches_with_same_name': only ask the user which person they mean when it is true."

    override val parameters = listOf(
        ToolParameter(
            name = "query",
            type = ToolParameter.Type.STRING,
            description = "Person name, parent name, partner name, child name, friend_of, month (e.g. 'oktober'), year (e.g. '1987'), or 'alla' to list all."
        )
    )

    override suspend fun execute(arguments: ToolArguments): Map<String, Any?> {
        val rawQuery = arguments.string("query")
            ?: throw ToolException("The 'query' parameter is required.")
        val today = clock.todayIn(timeZone())
        val trimmedLower = rawQuery.trim().lowercase()
        val candidates = extractSearchCandidates(rawQuery)

        val allEntries = repository.getEntriesSnapshot()

        // 1. Check if the query asks for all entries ("*", "all", "alla", "everyone")
        if (trimmedLower in ALL_QUERIES) {
            val allFlattened = allEntries.flattenBirthdays(today)
            val families = allEntries.mapIndexed { index, entry ->
                entry.toFamilyMap(today = today, familyIndex = index + 1)
            }
            return mapOf(
                "query" to rawQuery,
                "family_count" to families.size,
                "has_multiple_matches_with_same_name" to false,
                "summary" to "Hittade ${families.size} familjer (${allFlattened.size} födelsedagar): " +
                    allFlattened.joinToString(" | ") { it.toInfoSv() },
                "families" to families
            )
        }

        // 2. Check if the query is a month name (e.g. "oktober", "i maj") or 4-digit birth year ("1987")
        val queriedMonth: Month? = DateExpressions.parseMonthFromText(rawQuery)
        val queriedYear: String? = Regex("""\b(19\d{2}|20\d{2})\b""").find(rawQuery)?.groupValues?.get(1)

        if (queriedMonth != null || queriedYear != null) {
            val matchingFlattened = allEntries.flattenBirthdays(today).filter { p ->
                (queriedMonth == null || p.birthday.month == queriedMonth) &&
                    (queriedYear == null || p.birthday.year.toString() == queriedYear)
            }
            val matchingEntriesByDate = allEntries.filter { entry ->
                matchesDateFilter(entry.birthday, queriedMonth, queriedYear) ||
                    matchesDateFilter(entry.partner?.birthday, queriedMonth, queriedYear) ||
                    entry.children.any { matchesDateFilter(it.birthday, queriedMonth, queriedYear) }
            }
            val families = matchingEntriesByDate.mapIndexed { index, entry ->
                entry.toFamilyMap(today = today, familyIndex = index + 1)
            }
            val summaryText = if (matchingFlattened.isEmpty()) {
                "Ingen person hittades för '$rawQuery' (No matches found for '$rawQuery')."
            } else {
                "SV: ${matchingFlattened.joinToString(" | ") { it.toInfoSv() }} || EN: ${matchingFlattened.joinToString(" | ") { it.toInfoEn() }}"
            }
            return mapOf(
                "query" to rawQuery,
                "family_count" to families.size,
                "has_multiple_matches_with_same_name" to false,
                "summary" to summaryText,
                "families" to families
            )
        }

        // 3. Prefer exact person/partner/child name matches first so:
        //    - "Oscar", "Emelie", or "Lea" matches the person rather than every entry with friend_of="Oscar"
        //    - "Mats" doesn't substring-match "Matilda", "Ida" -> "Frida", etc.
        val exactPersonMatchingEntries = allEntries.filter { entry ->
            matchesExactCandidate(entry.name, candidates, rawQuery) ||
                matchesExactCandidate(entry.partner?.name, candidates, rawQuery) ||
                entry.children.any { child -> matchesExactCandidate(child.name, candidates, rawQuery) }
        }
        val exactFriendOfEntries = if (exactPersonMatchingEntries.isEmpty()) {
            allEntries.filter { entry ->
                matchesExactCandidate(entry.friendOf, candidates, rawQuery)
            }
        } else {
            emptyList()
        }
        val exactMatchingEntries = exactPersonMatchingEntries.ifEmpty { exactFriendOfEntries }
        val useExactOnly = exactMatchingEntries.isNotEmpty()
        val matchedByFriendOfOnly = exactPersonMatchingEntries.isEmpty() && exactFriendOfEntries.isNotEmpty()

        val matchingEntries = if (useExactOnly) {
            exactMatchingEntries
        } else {
            val substringPersonEntries = allEntries.filter { entry ->
                matchesAnyCandidate(entry.name, candidates, rawQuery) ||
                    matchesAnyCandidate(entry.partner?.name, candidates, rawQuery) ||
                    entry.children.any { child -> matchesAnyCandidate(child.name, candidates, rawQuery) }
            }
            substringPersonEntries.ifEmpty {
                allEntries.filter { entry -> matchesAnyCandidate(entry.friendOf, candidates, rawQuery) }
            }
        }

        fun nameMatches(value: String?): Boolean =
            if (useExactOnly) matchesExactCandidate(value, candidates, rawQuery)
            else matchesAnyCandidate(value, candidates, rawQuery)

        val families = matchingEntries.mapIndexed { index, entry ->
            val parentOrFamilyMatched = nameMatches(entry.name) ||
                nameMatches(entry.partner?.name) ||
                (matchedByFriendOfOnly && nameMatches(entry.friendOf))

            val scopedEntry = if (parentOrFamilyMatched) {
                entry
            } else {
                entry.copy(
                    birthday = null,
                    partner = entry.partner?.copy(birthday = null),
                    children = entry.children.filter { child -> nameMatches(child.name) }
                )
            }
            scopedEntry.toFamilyMap(
                today = today,
                familyIndex = index + 1,
                includeMissingParentBirthdayNote = parentOrFamilyMatched
            )
        }

        val matchedIndividuals = matchingEntries
            .flattenBirthdays(today)
            .filter { person -> nameMatches(person.name) }

        val duplicateParentNames = matchingEntries
            .groupingBy { it.name.lowercase() }
            .eachCount()
            .filter { (name, count) -> count > 1 && nameMatches(name) }
            .keys

        val duplicateMatchedIndividualNames = matchedIndividuals
            .groupingBy { it.name.lowercase() }
            .eachCount()
            .filter { it.value > 1 }
            .keys

        val duplicateNames = duplicateParentNames + duplicateMatchedIndividualNames
        val needsDisambiguation = duplicateNames.isNotEmpty()

        return buildMap {
            put("query", rawQuery)
            put("family_count", families.size)
            put("has_multiple_matches_with_same_name", needsDisambiguation)
            put(
                "summary",
                buildSearchSummary(
                    rawQuery = rawQuery,
                    matchingEntries = matchingEntries,
                    matchedIndividuals = matchedIndividuals,
                    needsDisambiguation = needsDisambiguation,
                    nameMatches = ::nameMatches,
                    today = today
                )
            )
            put("families", families)
            if (needsDisambiguation) {
                put(
                    "disambiguation_note",
                    "Multiple people share the name(s): ${duplicateNames.joinToString()}. " +
                        "List each matching person's birthday and children, and ask the user which one they mean."
                )
            }
        }
    }

    private fun matchesDateFilter(birthdayStr: String?, month: Month?, year: String?): Boolean {
        val date = birthdayStr?.toLocalDateOrNull() ?: return false
        return (month == null || date.month == month) &&
            (year == null || date.year.toString() == year)
    }

    private fun buildSearchSummary(
        rawQuery: String,
        matchingEntries: List<PersonEntry>,
        matchedIndividuals: List<FlatBirthdayPerson>,
        needsDisambiguation: Boolean,
        nameMatches: (String?) -> Boolean,
        today: LocalDate
    ): String {
        if (matchingEntries.isEmpty()) {
            return "Ingen person eller familj hittades för '$rawQuery' (No person or family found matching '$rawQuery')."
        }
        if (!needsDisambiguation && matchedIndividuals.size == 1) {
            val p = matchedIndividuals.single()
            val matchingEntry = matchingEntries.singleOrNull()
            val allFlat = matchingEntry?.let { listOf(it).flattenBirthdays(today) }.orEmpty()

            val partnerSummarySv = p.partnerName?.let { pName ->
                val flatPartner = allFlat.firstOrNull { it.name.equals(pName, ignoreCase = true) }
                if (flatPartner != null) {
                    " Partner: ${flatPartner.toInfoSv()}"
                } else {
                    " Partner: $pName (ingen födelsedag sparad)."
                }
            }.orEmpty()

            val partnerSummaryEn = p.partnerName?.let { pName ->
                val flatPartner = allFlat.firstOrNull { it.name.equals(pName, ignoreCase = true) }
                if (flatPartner != null) {
                    " Partner: ${flatPartner.toInfoEn()}"
                } else {
                    " Partner: $pName (no birthday saved)."
                }
            }.orEmpty()

            val isParentOrPartner = matchingEntry != null &&
                (matchingEntry.name.equals(p.name, ignoreCase = true) ||
                    matchingEntry.partner?.name.equals(p.name, ignoreCase = true))

            val childrenSummarySv = if (isParentOrPartner && matchingEntry.children.isNotEmpty()) {
                val kids = matchingEntry.children.map { child ->
                    val flatKid = allFlat.firstOrNull { it.name == child.name && it.birthday.toString() == child.birthday }
                    flatKid?.toInfoSv() ?: "${child.name} (född ${child.birthday})"
                }
                " Barn: ${kids.joinToString(" ")}"
            } else if (p.parentNames.isNotEmpty()) {
                " Föräldrar: ${p.parentNames.joinToString(", ")}."
            } else {
                ""
            }

            val childrenSummaryEn = if (isParentOrPartner && matchingEntry.children.isNotEmpty()) {
                val kids = matchingEntry.children.map { child ->
                    val flatKid = allFlat.firstOrNull { it.name == child.name && it.birthday.toString() == child.birthday }
                    flatKid?.toInfoEn() ?: "${child.name} (born ${child.birthday})"
                }
                " Children: ${kids.joinToString(" ")}"
            } else if (p.parentNames.isNotEmpty()) {
                " Parents: ${p.parentNames.joinToString(", ")}."
            } else {
                ""
            }

            return "SV: ${p.toInfoSv()}$partnerSummarySv$childrenSummarySv | EN: ${p.toInfoEn()}$partnerSummaryEn$childrenSummaryEn (There is only 1 ${p.name}; answer directly.)"
        }

        // Handle parent/partner matched by name whose own birthday is null
        if (!needsDisambiguation && matchedIndividuals.isEmpty() && matchingEntries.size == 1) {
            val entry = matchingEntries.single()
            val allFlat = listOf(entry).flattenBirthdays(today)
            val kidsSv = entry.children.mapNotNull { child ->
                allFlat.firstOrNull { it.name == child.name }?.toInfoSv()
            }
            val kidsPartSv = if (kidsSv.isNotEmpty()) " Barn: ${kidsSv.joinToString(" ")}" else ""
            if (nameMatches(entry.name) && entry.birthday == null) {
                return "SV: ${entry.name} har ingen födelsedag sparad (okänd ålder).$kidsPartSv | EN: ${entry.name} has no birthday saved (age unknown)."
            }
        }

        // Multiple matches (e.g. disambiguation between two Alex, or friend_of query)
        val familySummaries = matchingEntries.mapIndexed { idx, entry ->
            val flat = listOf(entry).flattenBirthdays(today)
            val parentFlat = flat.firstOrNull { it.name == entry.name && it.birthday.toString() == entry.birthday }
            val parentInfo = parentFlat?.toInfoSv() ?: "${entry.name} (födelsedag: ${entry.birthday ?: "okänd"})"
            val kidsInfo = if (entry.children.isNotEmpty()) {
                val kids = entry.children.map { c ->
                    flat.firstOrNull { it.name == c.name && it.birthday.toString() == c.birthday }?.toInfoSv()
                        ?: "${c.name} (${c.birthday})"
                }
                " [Barn: ${kids.joinToString("; ")}]"
            } else {
                ""
            }
            "${idx + 1}) $parentInfo$kidsInfo"
        }
        return "Hittade ${matchingEntries.size} träffar för '$rawQuery': ${familySummaries.joinToString(" | ")}"
    }

    private companion object {
        val ALL_QUERIES = setOf("*", "all", "alla", "everyone", "alla personer", "all people")
    }
}
