package se.oscarb.bdai.agent.tools

import kotlinx.datetime.LocalDate
import se.oscarb.bdai.agent.domain.toSpokenDate
import se.oscarb.bdai.domain.FlatBirthdayPerson
import se.oscarb.bdai.domain.PersonEntry
import se.oscarb.bdai.domain.createFlatPerson
import se.oscarb.bdai.domain.toLocalDateOrNull

fun FlatBirthdayPerson.toToolMap(): Map<String, Any?> = buildMap {
    put("name", name)
    put("birthday", birthday.toString())
    put("spoken_birthday", birthday.toSpokenDate())
    put("age_now", ageNow)
    put("turning_age", turningAge)
    put("next_birthday", nextBirthday.toString())
    put("spoken_next_birthday", nextBirthday.toSpokenDate())
    put("days_until_birthday", daysUntilNextBirthday)
    put("parents", parentNames)
    put("children", childNames)
    put("partner", partnerName)
    put("friend_of", friendOf)
}

fun PersonEntry.toFamilyMap(today: LocalDate, familyIndex: Int? = null): Map<String, Any?> = buildMap {
    if (familyIndex != null) {
        put("family_index", familyIndex)
    }
    put("name", name)
    put("birthday", birthday)
    val childNames = children.map { it.name }
    val parentFlat = birthday?.toLocalDateOrNull()?.let { bday ->
        createFlatPerson(
            key = "person:$name:$birthday",
            name = name,
            birthday = bday,
            friendOf = friendOf,
            parentNames = emptyList(),
            childNames = childNames,
            partnerName = partner?.name,
            today = today
        )
    }
    if (parentFlat != null) {
        put("spoken_birthday", parentFlat.birthday.toSpokenDate())
        put("age_now", parentFlat.ageNow)
        put("turning_age", parentFlat.turningAge)
        put("next_birthday", parentFlat.nextBirthday.toString())
        put("days_until_birthday", parentFlat.daysUntilNextBirthday)
    }
    put("friend_of", friendOf)
    put(
        "partner",
        partner?.let { p ->
            val partnerFlat = p.birthday?.toLocalDateOrNull()?.let { bday ->
                createFlatPerson(
                    key = "partner:${p.name}:${p.birthday}",
                    name = p.name,
                    birthday = bday,
                    friendOf = friendOf,
                    parentNames = emptyList(),
                    childNames = childNames,
                    partnerName = name,
                    today = today
                )
            }
            buildMap {
                put("name", p.name)
                put("birthday", p.birthday)
                if (partnerFlat != null) {
                    put("spoken_birthday", partnerFlat.birthday.toSpokenDate())
                    put("age_now", partnerFlat.ageNow)
                    put("turning_age", partnerFlat.turningAge)
                    put("next_birthday", partnerFlat.nextBirthday.toString())
                    put("days_until_birthday", partnerFlat.daysUntilNextBirthday)
                }
            }
        }
    )
    val parentList = buildList {
        add(name)
        partner?.name?.let { add(it) }
    }
    put(
        "children",
        children.map { child ->
            val childFlat = child.birthday.toLocalDateOrNull()?.let { bday ->
                createFlatPerson(
                    key = "child:$name:${child.name}:${child.birthday}",
                    name = child.name,
                    birthday = bday,
                    friendOf = friendOf,
                    parentNames = parentList,
                    childNames = emptyList(),
                    partnerName = null,
                    today = today
                )
            }
            buildMap {
                put("name", child.name)
                put("birthday", child.birthday)
                put("parents", parentList)
                put("friend_of", friendOf)
                if (childFlat != null) {
                    put("spoken_birthday", childFlat.birthday.toSpokenDate())
                    put("age_now", childFlat.ageNow)
                    put("turning_age", childFlat.turningAge)
                    put("next_birthday", childFlat.nextBirthday.toString())
                    put("days_until_birthday", childFlat.daysUntilNextBirthday)
                }
            }
        }
    )
}

/**
 * Strips English (`'s`, `’s`, `s'`) and Swedish (`s`) possessive suffixes from a single name
 * when needed while preserving the base name.
 */
fun stripPossessive(raw: String): String {
    val trimmed = raw.trim()
        .removeSuffix("'s")
        .removeSuffix("’s")
        .removeSuffix("'S")
        .removeSuffix("’S")
        .removeSuffix("'")
        .removeSuffix("’")
        .trim()
    return if (trimmed.length >= 4 && (trimmed.endsWith('s') || trimmed.endsWith('S'))) {
        trimmed.dropLast(1)
    } else {
        trimmed
    }
}

/**
 * Extracts normalized search candidate terms from a user or LLM query, handling Swedish and
 * English possessives like `"Simons"` -> `["simons", "simon"]` and `"Alex's"` -> `["alex"]`.
 */
fun extractSearchCandidates(query: String): List<String> {
    val rawTokens = query.trim()
        .split(TOKEN_SPLIT_REGEX)
        .map {
            it.trim()
                .removeSuffix("'s")
                .removeSuffix("’s")
                .removeSuffix("'S")
                .removeSuffix("’S")
                .removeSuffix("'")
                .removeSuffix("’")
                .lowercase()
        }
        .filter { it.length >= 2 && it !in QUERY_STOP_WORDS }

    if (rawTokens.isEmpty()) {
        val fallback = stripPossessive(query).lowercase()
        return listOfNotNull(query.trim().lowercase().takeIf { it.isNotEmpty() }, fallback.takeIf { it.isNotEmpty() }).distinct()
    }

    return buildList {
        for (token in rawTokens) {
            add(token)
            if (token.length >= 4 && token.endsWith("s")) {
                add(token.dropLast(1))
            }
        }
    }.distinct()
}

fun matchesAnyCandidate(value: String?, candidates: List<String>, rawQuery: String): Boolean {
    if (value.isNullOrBlank()) return false
    val lowerValue = value.trim().lowercase()
    val strippedRaw = stripPossessive(rawQuery).lowercase()
    if (lowerValue == rawQuery.trim().lowercase() || (strippedRaw.isNotEmpty() && lowerValue == strippedRaw)) {
        return true
    }
    return candidates.any { candidate ->
        lowerValue == candidate || lowerValue.contains(candidate)
    }
}

fun unknownDateMessage(input: String): String {
    return "Couldn't understand the date '$input'. Pass an ISO date (YYYY-MM-DD) or natural wording like ${DateExpressions.DATE_EXAMPLES}."
}

private val TOKEN_SPLIT_REGEX = Regex("""[^\p{L}\p{N}'’]+""")

private val QUERY_STOP_WORDS = setOf(
    "hur", "gamla", "gammal", "är", "när", "fyller", "år", "barn", "barnen", "ungar",
    "partner", "fru", "man", "sambo", "födelsedag", "födelsedagar", "vem", "vilka", "har", "och",
    "how", "old", "is", "are", "when", "does", "do", "birthday", "birthdays", "kids", "kid",
    "children", "child", "wife", "husband", "family", "familj", "of", "the", "for", "and", "who"
)
