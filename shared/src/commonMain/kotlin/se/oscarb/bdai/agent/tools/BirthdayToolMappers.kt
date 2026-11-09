package se.oscarb.bdai.agent.tools

import kotlinx.datetime.LocalDate
import se.oscarb.bdai.agent.domain.toEnglishDate
import se.oscarb.bdai.agent.domain.toEnglishDayMonth
import se.oscarb.bdai.agent.domain.toSwedishDate
import se.oscarb.bdai.agent.domain.toSwedishDayMonth
import se.oscarb.bdai.domain.FlatBirthdayPerson
import se.oscarb.bdai.domain.PersonEntry
import se.oscarb.bdai.domain.createFlatPerson
import se.oscarb.bdai.domain.toLocalDateOrNull

fun FlatBirthdayPerson.toUpcomingSv(): String = when (daysUntilNextBirthday) {
    0 -> "$name fyller $turningAge år idag (${birthday.toSwedishDayMonth()})."
    1 -> "$name fyller $turningAge år imorgon den ${birthday.toSwedishDayMonth()}."
    else -> "$name fyller $turningAge år den ${birthday.toSwedishDayMonth()} (om $daysUntilNextBirthday dagar)."
}

fun FlatBirthdayPerson.toUpcomingEn(): String = when (daysUntilNextBirthday) {
    0 -> "$name turns $turningAge today (${birthday.toEnglishDayMonth()})."
    1 -> "$name turns $turningAge tomorrow on ${birthday.toEnglishDayMonth()}."
    else -> "$name turns $turningAge on ${birthday.toEnglishDayMonth()} (in $daysUntilNextBirthday days)."
}

fun FlatBirthdayPerson.toInfoSv(): String = when (daysUntilNextBirthday) {
    0 -> "$name fyller $turningAge år idag den ${birthday.toSwedishDayMonth()} (född ${birthday.toSwedishDate()})."
    1 -> "$name är $ageNow år (född ${birthday.toSwedishDate()}, fyller $turningAge år imorgon den ${birthday.toSwedishDayMonth()})."
    else -> "$name är $ageNow år (född ${birthday.toSwedishDate()}, fyller $turningAge år den ${birthday.toSwedishDayMonth()} om $daysUntilNextBirthday dagar)."
}

fun FlatBirthdayPerson.toInfoEn(): String = when (daysUntilNextBirthday) {
    0 -> "$name turns $turningAge today on ${birthday.toEnglishDayMonth()} (born ${birthday.toEnglishDate()})."
    1 -> "$name is $ageNow years old (born ${birthday.toEnglishDate()}, turns $turningAge tomorrow on ${birthday.toEnglishDayMonth()})."
    else -> "$name is $ageNow years old (born ${birthday.toEnglishDate()}, turns $turningAge on ${birthday.toEnglishDayMonth()} in $daysUntilNextBirthday days)."
}

fun FlatBirthdayPerson.toToolMap(): Map<String, Any?> = buildMap {
    put("name", name)
    put("birthday", birthday.toString())
    put("age_now", ageNow)
    put("turning_age", turningAge)
    put("days_until_birthday", daysUntilNextBirthday)
    put("info_sv", toUpcomingSv())
    put("info_en", toUpcomingEn())
    if (parentNames.isNotEmpty()) {
        put("parents", parentNames)
    }
    if (childNames.isNotEmpty()) {
        put("children", childNames)
    }
    if (!partnerName.isNullOrBlank()) {
        put("partner", partnerName)
    }
    if (!friendOf.isNullOrBlank()) {
        put("friend_of", friendOf)
    }
}

fun PersonEntry.toFamilyMap(
    today: LocalDate,
    familyIndex: Int? = null,
    includeMissingParentBirthdayNote: Boolean = true
): Map<String, Any?> = buildMap {
    if (familyIndex != null) {
        put("family_index", familyIndex)
    }
    put("name", name)
    if (birthday != null) {
        put("birthday", birthday)
    }
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
        put("age_now", parentFlat.ageNow)
        put("turning_age", parentFlat.turningAge)
        put("days_until_birthday", parentFlat.daysUntilNextBirthday)
        put("info_sv", parentFlat.toInfoSv())
        put("info_en", parentFlat.toInfoEn())
    } else if (includeMissingParentBirthdayNote) {
        put("info_sv", "$name har ingen födelsedag sparad (okänd ålder).")
        put("info_en", "$name has no birthday saved (age unknown).")
    }
    if (!friendOf.isNullOrBlank()) {
        put("friend_of", friendOf)
    }
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
        put(
            "partner",
            buildMap {
                put("name", p.name)
                if (p.birthday != null) {
                    put("birthday", p.birthday)
                }
                if (partnerFlat != null) {
                    put("age_now", partnerFlat.ageNow)
                    put("turning_age", partnerFlat.turningAge)
                    put("days_until_birthday", partnerFlat.daysUntilNextBirthday)
                    put("info_sv", partnerFlat.toInfoSv())
                    put("info_en", partnerFlat.toInfoEn())
                } else if (includeMissingParentBirthdayNote) {
                    put("info_sv", "${p.name} har ingen födelsedag sparad (okänd ålder).")
                    put("info_en", "${p.name} has no birthday saved (age unknown).")
                }
            }
        )
    }
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
                if (!friendOf.isNullOrBlank()) {
                    put("friend_of", friendOf)
                }
                if (childFlat != null) {
                    put("age_now", childFlat.ageNow)
                    put("turning_age", childFlat.turningAge)
                    put("days_until_birthday", childFlat.daysUntilNextBirthday)
                    put("info_sv", childFlat.toInfoSv())
                    put("info_en", childFlat.toInfoEn())
                }
            }
        }
    )
}

fun stripApostrophePossessive(raw: String): String =
    raw.trim()
        .removeSuffix("'s")
        .removeSuffix("’s")
        .removeSuffix("'S")
        .removeSuffix("’S")
        .removeSuffix("'")
        .removeSuffix("’")
        .trim()

/**
 * Strips English (`'s`, `’s`, `s'`) and Swedish (`s`) possessive suffixes from a single name
 * when needed while preserving the base name.
 */
fun stripPossessive(raw: String): String {
    val trimmed = stripApostrophePossessive(raw)
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
        .map { stripApostrophePossessive(it).lowercase() }
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

fun matchesExactCandidate(value: String?, candidates: List<String>, rawQuery: String): Boolean {
    if (value.isNullOrBlank()) return false
    val lowerValue = value.trim().lowercase()
    val strippedRaw = stripPossessive(rawQuery).lowercase()
    if (lowerValue == rawQuery.trim().lowercase() ||
        lowerValue == stripApostrophePossessive(rawQuery).lowercase() ||
        (strippedRaw.isNotEmpty() && lowerValue == strippedRaw)
    ) {
        return true
    }
    val valueTokens = lowerValue.split(' ', '-')
    return candidates.any { candidate ->
        lowerValue == candidate || valueTokens.any { it == candidate }
    }
}

fun matchesAnyCandidate(value: String?, candidates: List<String>, rawQuery: String): Boolean {
    if (matchesExactCandidate(value, candidates, rawQuery)) return true
    if (value.isNullOrBlank()) return false
    val lowerValue = value.trim().lowercase()
    return candidates.any { candidate ->
        candidate.length >= 3 && lowerValue.contains(candidate)
    }
}

fun resolveExistingPersonName(entries: List<PersonEntry>, rawName: String): String? {
    val trimmed = rawName.trim()
    val stripped = stripPossessive(trimmed).ifEmpty { trimmed }
    for (entry in entries) {
        if (entry.name.equals(trimmed, ignoreCase = true)) return entry.name
        if (entry.partner?.name.equals(trimmed, ignoreCase = true)) return entry.partner?.name
        entry.children.firstOrNull { it.name.equals(trimmed, ignoreCase = true) }?.let { return it.name }
    }
    for (entry in entries) {
        if (entry.name.equals(stripped, ignoreCase = true)) return entry.name
        if (entry.partner?.name.equals(stripped, ignoreCase = true)) return entry.partner?.name
        entry.children.firstOrNull { it.name.equals(stripped, ignoreCase = true) }?.let { return it.name }
    }
    return null
}

fun findExistingBirthday(
    entries: List<PersonEntry>,
    name: String,
    parentNameHint: String? = null
): LocalDate? {
    val trimmedName = name.trim()
    val strippedName = stripPossessive(trimmedName).ifEmpty { trimmedName }
    val trimmedParent = parentNameHint?.trim()?.takeIf { it.isNotEmpty() }
    val strippedParent = trimmedParent?.let { stripPossessive(it).ifEmpty { it } }

    fun matchesTarget(candidate: String): Boolean =
        candidate.equals(trimmedName, ignoreCase = true) ||
            candidate.equals(strippedName, ignoreCase = true)

    val matchingDates = mutableListOf<LocalDate>()
    for (entry in entries) {
        val parentMatches = trimmedParent == null ||
            entry.name.equals(trimmedParent, ignoreCase = true) ||
            entry.name.equals(strippedParent, ignoreCase = true) ||
            entry.partner?.name?.equals(trimmedParent, ignoreCase = true) == true ||
            entry.partner?.name?.equals(strippedParent, ignoreCase = true) == true

        if (trimmedParent == null) {
            if (matchesTarget(entry.name)) {
                entry.birthday?.toLocalDateOrNull()?.let { matchingDates += it }
            }
            val partner = entry.partner
            if (partner != null && matchesTarget(partner.name)) {
                partner.birthday?.toLocalDateOrNull()?.let { matchingDates += it }
            }
        }
        if (parentMatches) {
            for (child in entry.children) {
                if (matchesTarget(child.name)) {
                    child.birthday.toLocalDateOrNull()?.let { matchingDates += it }
                }
            }
        }
    }
    return matchingDates.distinct().singleOrNull()
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
