package se.oscarb.bdai.agent.tools

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus

/**
 * Parses natural date and birthday expressions in both English and Swedish into exact [LocalDate]
 * instances so the LLM never has to perform mental date arithmetic.
 */
object DateExpressions {

    const val DATE_EXAMPLES =
        "'1988-04-15', '26 oktober', 'hon fyllde 1 den 26 oktober', 'today', 'yesterday', '15 april 1988', 'idag', 'igår'"

    fun parseBirthday(
        input: String,
        today: LocalDate,
        ageHint: Int? = null,
        existingBirthday: LocalDate? = null
    ): LocalDate? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            if (ageHint != null && ageHint in 0..130 && existingBirthday != null) {
                return computeBirthDateWithAge(
                    month = existingBirthday.month,
                    day = existingBirthday.day,
                    age = ageHint,
                    mode = AgeMode.TURNED_OR_CURRENT,
                    today = today
                )
            }
            return null
        }

        val inlineAge = extractInlineAge(trimmed)
        val effectiveAge = ageHint?.takeIf { it in 0..130 } ?: inlineAge?.age
        val ageMode = inlineAge?.mode ?: AgeMode.TURNED_OR_CURRENT
        val cleanedInput = inlineAge?.remainingText?.ifBlank { trimmed } ?: trimmed

        if (ISO_DATE.matches(cleanedInput)) {
            val iso = runCatching { LocalDate.parse(cleanedInput) }.getOrNull() ?: return null
            if (effectiveAge != null && effectiveAge > 0 && iso.year == today.year) {
                return computeBirthDateWithAge(iso.month, iso.day, effectiveAge, ageMode, today) ?: iso
            }
            return iso
        }

        // If the input only specifies a birth year (e.g. "2025", "född 2025", "förra året")
        // and we have an existing birthday for this person, keep their month & day and update the year.
        val yearOnly = extractYearOnly(cleanedInput, today)
        if (yearOnly != null) {
            if (existingBirthday != null) {
                return dateOrNull(yearOnly, existingBirthday.month, existingBirthday.day)
                    ?: if (existingBirthday.month == Month.FEBRUARY && existingBirthday.day == 29) {
                        dateOrNull(yearOnly, Month.FEBRUARY, 28)
                    } else {
                        null
                    }
            }
            return null
        }

        val words = words(cleanedInput)
        if (words.isEmpty()) {
            if (effectiveAge != null && existingBirthday != null) {
                return computeBirthDateWithAge(
                    month = existingBirthday.month,
                    day = existingBirthday.day,
                    age = effectiveAge,
                    mode = ageMode,
                    today = today
                )
            }
            return null
        }

        val hasExplicitYear = words.any { word ->
            val n = word.toIntOrNull()
            n != null && n in 1900..(today.year + 1)
        } || LAST_YEAR_REGEX.containsMatchIn(cleanedInput) ||
            THIS_YEAR_REGEX.containsMatchIn(cleanedInput) ||
            SLASH_DATE_WITH_YEAR_REGEX.containsMatchIn(cleanedInput)

        val baseDate = parseIsoDate(words)
            ?: parseSlashDate(cleanedInput, today, preferPastOrCurrentYear = true)
            ?: parseRelativeDate(words, today)
            ?: parseCalendarDate(words, today, preferPastOrCurrentYear = true, rawInput = cleanedInput)

        if (baseDate == null) {
            if (effectiveAge != null && existingBirthday != null) {
                return computeBirthDateWithAge(
                    month = existingBirthday.month,
                    day = existingBirthday.day,
                    age = effectiveAge,
                    mode = ageMode,
                    today = today
                )
            }
            return null
        }

        if (effectiveAge != null && (!hasExplicitYear || (effectiveAge > 0 && baseDate.year == today.year))) {
            return computeBirthDateWithAge(baseDate.month, baseDate.day, effectiveAge, ageMode, today) ?: baseDate
        }

        // If updating only the day/month of an existing person (e.g. "26 oktober") without specifying
        // a year or age, preserve their existing birth year rather than resetting to today.year.
        if (!hasExplicitYear && effectiveAge == null && existingBirthday != null) {
            return dateOrNull(existingBirthday.year, baseDate.month, baseDate.day) ?: baseDate
        }

        return baseDate
    }

    fun extractYearOnly(input: String, today: LocalDate): Int? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        if (SLASH_DATE_REGEX.containsMatchIn(trimmed)) return null
        val w = words(trimmed).filter { it !in YEAR_ONLY_FILLER_WORDS }
        if (w.any { parseMonth(it) != null || parseWeekday(it) != null }) return null
        if (w.isEmpty()) {
            if (LAST_YEAR_REGEX.containsMatchIn(trimmed)) return today.year - 1
            if (THIS_YEAR_REGEX.containsMatchIn(trimmed)) return today.year
            return null
        }
        if (LAST_YEAR_REGEX.matches(trimmed)) return today.year - 1
        if (THIS_YEAR_REGEX.matches(trimmed)) return today.year
        val singleWord = w.singleOrNull() ?: return null
        val year = singleWord.toIntOrNull() ?: return null
        return year.takeIf { it in 1900..(today.year + 1) }
    }

    fun parseMonthFromText(input: String?): Month? {
        if (input.isNullOrBlank()) return null
        return words(input).firstNotNullOfOrNull { parseMonth(it) }
    }

    private enum class AgeMode {
        TURNED_OR_CURRENT,
        TURNING_NEXT
    }

    private data class InlineAgeMatch(
        val age: Int,
        val mode: AgeMode,
        val remainingText: String
    )

    private fun extractInlineAge(input: String): InlineAgeMatch? {
        TURNED_AGE_REGEX.find(input)?.let { match ->
            val age = parseNumber(match.groupValues[1].lowercase()) ?: return@let
            val remaining = input.removeRange(match.range).trim()
            return InlineAgeMatch(age = age, mode = AgeMode.TURNED_OR_CURRENT, remainingText = remaining)
        }
        TURNING_AGE_REGEX.find(input)?.let { match ->
            val age = parseNumber(match.groupValues[1].lowercase()) ?: return@let
            val remaining = input.removeRange(match.range).trim()
            return InlineAgeMatch(age = age, mode = AgeMode.TURNING_NEXT, remainingText = remaining)
        }
        CURRENT_AGE_WITH_UNIT_REGEX.find(input)?.let { match ->
            val age = parseNumber(match.groupValues[1].lowercase()) ?: return@let
            val remaining = input.removeRange(match.range).trim()
            return InlineAgeMatch(age = age, mode = AgeMode.TURNED_OR_CURRENT, remainingText = remaining)
        }
        return null
    }

    private fun computeBirthDateWithAge(
        month: Month,
        day: Int,
        age: Int,
        mode: AgeMode,
        today: LocalDate
    ): LocalDate? {
        val occurrenceThisYear = dateOrNull(today.year, month, day)
            ?: dateOrNull(today.year, Month.FEBRUARY, 28)
            ?: return null

        val birthYear = when (mode) {
            AgeMode.TURNING_NEXT -> {
                if (occurrenceThisYear >= today) {
                    today.year - age
                } else {
                    (today.year + 1) - age
                }
            }
            AgeMode.TURNED_OR_CURRENT -> {
                if (occurrenceThisYear <= today) {
                    today.year - age
                } else {
                    (today.year - 1) - age
                }
            }
        }
        return dateOrNull(birthYear, month, day)
            ?: if (month == Month.FEBRUARY && day == 29) dateOrNull(birthYear, Month.FEBRUARY, 28) else null
    }

    fun parseDate(input: String, today: LocalDate): LocalDate? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        if (ISO_DATE.matches(trimmed)) {
            return runCatching { LocalDate.parse(trimmed) }.getOrNull()
        }
        val words = words(trimmed)
        if (words.isEmpty()) return null
        return parseIsoDate(words)
            ?: parseRelativeDate(words, today)
            ?: parseCalendarDate(words, today, preferPastOrCurrentYear = false)
    }

    fun parseDaysLookahead(input: String?): Int? {
        if (input.isNullOrBlank()) return null
        val normalized = input.trim().lowercase()
        normalized.toIntOrNull()?.takeIf { it in 1..366 }?.let { return it }

        return when (normalized) {
            "today", "idag" -> 0
            "tomorrow", "imorgon", "i morgon" -> 1
            "this week", "next week", "week", "denna vecka", "nästa vecka", "veckan", "7 days", "next 7 days" -> 7
            "two weeks", "2 weeks", "next 2 weeks", "14 days", "två veckor" -> 14
            "this month", "next month", "month", "denna månad", "nästa månad", "månaden", "30 days", "next 30 days" -> 30
            "this year", "year", "i år", "året", "all", "alla" -> 365
            else -> {
                val numberMatch = Regex("""(\d+)""").find(normalized)?.groupValues?.get(1)?.toIntOrNull()
                when {
                    numberMatch != null && ("week" in normalized || "veck" in normalized) ->
                        (numberMatch * 7).coerceIn(1, 366)
                    numberMatch != null && ("month" in normalized || "månad" in normalized) ->
                        (numberMatch * 30).coerceIn(1, 366)
                    numberMatch != null -> numberMatch.coerceIn(1, 366)
                    else -> null
                }
            }
        }
    }

    private fun parseIsoDate(words: List<String>): LocalDate? {
        val text = words.singleOrNull() ?: return null
        if (!ISO_DATE.matches(text)) return null
        return runCatching { LocalDate.parse(text) }.getOrNull()
    }

    private fun parseRelativeDate(words: List<String>, today: LocalDate): LocalDate? {
        val text = words.joinToString(" ")
        when (text) {
            "today", "tonight", "now", "idag", "i dag" -> return today
            "tomorrow", "tmrw", "imorgon", "i morgon" -> return today.plus(1, DateTimeUnit.DAY)
            "day after tomorrow", "övermorgon", "i övermorgon" -> return today.plus(2, DateTimeUnit.DAY)
            "yesterday", "igår", "i går" -> return today.minus(1, DateTimeUnit.DAY)
            "day before yesterday", "förrgår", "i förrgår" -> return today.minus(2, DateTimeUnit.DAY)
            "next week", "nästa vecka" -> return startOfWeek(today).plus(1, DateTimeUnit.WEEK)
            "next month", "nästa månad" -> return startOfMonth(today).plus(1, DateTimeUnit.MONTH)
            "next year", "nästa år" -> return LocalDate(today.year + 1, 1, 1)
        }

        parseWeekdayPhrase(words, today)?.let { return it }

        if (words.size == 3 && words[0] in listOf("in", "om")) {
            val amount = parseNumber(words[1]) ?: return null
            val unit = parseUnit(words[2]) ?: return null
            return today.plus(amount, unit)
        }

        if (words.size == 3 && words[2] in listOf("ago", "sedan")) {
            val amount = parseNumber(words[0]) ?: return null
            val unit = parseUnit(words[1]) ?: return null
            return today.minus(amount, unit)
        }

        return null
    }

    private fun parseWeekdayPhrase(words: List<String>, today: LocalDate): LocalDate? {
        return when (words.size) {
            1 -> parseWeekday(words[0])?.let { nextWeekday(today, it, includeToday = false) }
            2 -> {
                val weekday = parseWeekday(words[1]) ?: return null
                when (words[0]) {
                    "this", "denna" -> nextWeekday(today, weekday, includeToday = true)
                    "coming", "kommande" -> nextWeekday(today, weekday, includeToday = false)
                    "next", "nästa" -> weekdayInWeek(startOfWeek(today).plus(1, DateTimeUnit.WEEK), weekday)
                    "last", "förra", "i" -> previousWeekday(today, weekday)
                    else -> null
                }
            }
            else -> null
        }
    }

    private fun parseSlashDate(
        rawInput: String,
        today: LocalDate,
        preferPastOrCurrentYear: Boolean
    ): LocalDate? {
        val match = SLASH_DATE_REGEX.find(rawInput.trim()) ?: return null
        val day = match.groupValues[1].toIntOrNull() ?: return null
        val monthNum = match.groupValues[2].toIntOrNull() ?: return null
        val month = runCatching { Month(monthNum) }.getOrNull() ?: return null
        val rawYear = match.groupValues[3].takeIf { it.isNotEmpty() }?.toIntOrNull()

        val explicitYear = when {
            rawYear != null && rawYear >= 1000 -> rawYear
            rawYear != null && rawYear in 0..99 -> {
                val twoDigitCurrent = today.year % 100
                if (rawYear <= twoDigitCurrent) 2000 + rawYear else 1900 + rawYear
            }
            LAST_YEAR_REGEX.containsMatchIn(rawInput) -> today.year - 1
            THIS_YEAR_REGEX.containsMatchIn(rawInput) -> today.year
            else -> null
        }

        if (explicitYear != null) {
            return dateOrNull(explicitYear, month, day)
        }

        return if (preferPastOrCurrentYear) {
            val candidateThisYear = dateOrNull(today.year, month, day)
            if (candidateThisYear != null && candidateThisYear <= today) {
                candidateThisYear
            } else {
                dateOrNull(today.year - 1, month, day) ?: candidateThisYear
            }
        } else {
            (today.year..today.year + 8).firstNotNullOfOrNull { year ->
                dateOrNull(year, month, day)?.takeIf { it >= today }
            }
        }
    }

    private fun parseCalendarDate(
        words: List<String>,
        today: LocalDate,
        preferPastOrCurrentYear: Boolean,
        rawInput: String = ""
    ): LocalDate? {
        val cleanedRaw = rawInput
            .replace(LAST_YEAR_REGEX, " ")
            .replace(THIS_YEAR_REGEX, " ")
        val baseWords = if (rawInput.isNotEmpty()) words(cleanedRaw) else words
        val dateWords = baseWords.filter { it !in setOf("of", "den", "i", "in") }
        val month = dateWords.firstNotNullOfOrNull { parseMonth(it) } ?: return null
        val numbers = dateWords.mapNotNull { word ->
            ORDINAL.matchEntire(word)?.groupValues?.get(1)?.toIntOrNull()
        }
        val unknownWords = dateWords.filter { parseMonth(it) == null && !ORDINAL.matches(it) }
        if (unknownWords.isNotEmpty()) return null

        val relativeYear = when {
            LAST_YEAR_REGEX.containsMatchIn(rawInput) -> today.year - 1
            THIS_YEAR_REGEX.containsMatchIn(rawInput) -> today.year
            else -> null
        }

        return when (numbers.size) {
            1 -> {
                val day = numbers.single()
                if (relativeYear != null) {
                    dateOrNull(relativeYear, month, day)
                } else if (preferPastOrCurrentYear) {
                    val candidateThisYear = dateOrNull(today.year, month, day)
                    if (candidateThisYear != null && candidateThisYear <= today) {
                        candidateThisYear
                    } else {
                        dateOrNull(today.year - 1, month, day) ?: candidateThisYear
                    }
                } else {
                    (today.year..today.year + 8).firstNotNullOfOrNull { year ->
                        dateOrNull(year, month, day)?.takeIf { it >= today }
                    }
                }
            }
            2 -> {
                val (day, year) = if (numbers[0] > 31) numbers[1] to numbers[0] else numbers[0] to numbers[1]
                if (year < 1000) null else dateOrNull(year, month, day)
            }
            else -> null
        }
    }

    private fun nextWeekday(today: LocalDate, weekday: DayOfWeek, includeToday: Boolean): LocalDate {
        val daysAhead = (weekday.isoDayNumber - today.dayOfWeek.isoDayNumber + 7) % 7
        val offset = if (daysAhead == 0 && !includeToday) 7 else daysAhead
        return today.plus(offset, DateTimeUnit.DAY)
    }

    private fun previousWeekday(today: LocalDate, weekday: DayOfWeek): LocalDate {
        val daysBehind = (today.dayOfWeek.isoDayNumber - weekday.isoDayNumber + 7) % 7
        val offset = if (daysBehind == 0) 7 else daysBehind
        return today.minus(offset, DateTimeUnit.DAY)
    }

    private fun weekdayInWeek(weekStart: LocalDate, weekday: DayOfWeek): LocalDate {
        return weekStart.plus(weekday.isoDayNumber - 1, DateTimeUnit.DAY)
    }

    private fun startOfWeek(date: LocalDate): LocalDate =
        date.minus(date.dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)

    private fun startOfMonth(date: LocalDate): LocalDate = LocalDate(date.year, date.month, 1)

    private fun dateOrNull(year: Int, month: Month, day: Int): LocalDate? {
        return runCatching { LocalDate(year, month.number, day) }.getOrNull()
    }

    private fun words(input: String): List<String> {
        return input.lowercase()
            .replace(PUNCTUATION, " ")
            .replace(A_COUPLE, "couple")
            .split(' ')
            .filter { it.isNotBlank() && it !in FILLER_WORDS }
    }

    private fun parseNumber(word: String): Int? {
        return word.toIntOrNull()?.takeIf { it in 0..1000 } ?: NUMBER_WORDS[word]
    }

    private fun parseUnit(word: String): DateTimeUnit.DateBased? = when (word) {
        "day", "days", "dag", "dagar" -> DateTimeUnit.DAY
        "week", "weeks", "vecka", "veckor" -> DateTimeUnit.WEEK
        "month", "months", "månad", "månader" -> DateTimeUnit.MONTH
        "year", "years", "år" -> DateTimeUnit.YEAR
        else -> null
    }

    private fun parseWeekday(word: String): DayOfWeek? = when (word) {
        "monday", "mon", "måndag", "måndags" -> DayOfWeek.MONDAY
        "tuesday", "tue", "tues", "tisdag", "tisdags" -> DayOfWeek.TUESDAY
        "wednesday", "wed", "onsdag", "onsdags" -> DayOfWeek.WEDNESDAY
        "thursday", "thu", "thur", "thurs", "torsdag", "torsdags" -> DayOfWeek.THURSDAY
        "friday", "fri", "fredag", "fredags" -> DayOfWeek.FRIDAY
        "saturday", "sat", "lördag", "lördags" -> DayOfWeek.SATURDAY
        "sunday", "sun", "söndag", "söndags" -> DayOfWeek.SUNDAY
        else -> null
    }

    private fun parseMonth(word: String): Month? = when (word) {
        "january", "jan", "januari" -> Month.JANUARY
        "february", "feb", "februari" -> Month.FEBRUARY
        "march", "mar", "mars" -> Month.MARCH
        "april", "apr" -> Month.APRIL
        "may", "maj" -> Month.MAY
        "june", "jun", "juni" -> Month.JUNE
        "july", "jul", "juli" -> Month.JULY
        "august", "aug", "augusti" -> Month.AUGUST
        "september", "sep", "sept" -> Month.SEPTEMBER
        "october", "oct", "oktober", "okt" -> Month.OCTOBER
        "november", "nov" -> Month.NOVEMBER
        "december", "dec" -> Month.DECEMBER
        else -> null
    }

    private val ISO_DATE = Regex("""\d{4}-\d{2}-\d{2}""")
    private val SLASH_DATE_REGEX = Regex("""\b(\d{1,2})/(\d{1,2})(?:[\s/-]+(\d{2,4}))?\b""")
    private val SLASH_DATE_WITH_YEAR_REGEX = Regex("""\b\d{1,2}/\d{1,2}[\s/-]+\d{2,4}\b""")
    private val LAST_YEAR_REGEX = Regex("""(?i)\b(förra\s+året|ifjol|i\s+fjol|last\s+year)\b""")
    private val THIS_YEAR_REGEX = Regex("""(?i)\b(i\s+år|detta\s+år|det\s+här\s+året|this\s+year)\b""")
    private val ORDINAL = Regex("""(\d{1,4})(st|nd|rd|th|:e|:a|e|a)?""")
    private val PUNCTUATION = Regex("""[,.!?;:()"']""")
    private val A_COUPLE = Regex("""\ba couple\b""")
    private val FILLER_WORDS = setOf(
        "on", "the", "due", "at", "born", "född", "födda", "föddes",
        "hon", "han", "hen", "som", "den", "och", "she", "he", "they", "who", "was", "is", "är"
    )
    private val YEAR_ONLY_FILLER_WORDS = setOf(
        "år", "året", "year", "in", "i", "till", "to", "from", "från"
    )
    private const val AGE_TOKEN_PATTERN =
        """\d{1,3}|en|ett|två|tre|fyra|fem|sex|sju|åtta|nio|tio|elva|tolv|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve"""

    private val TURNED_AGE_REGEX = Regex(
        """(?i)\b(?:(?:hon|han|hen|som|she|he|they|who)\s+)?(?:har\s+)?(?:fyllde|fyllt|blev|turned)\s+($AGE_TOKEN_PATTERN)(?:\s*(?:år(?:\s+gammal)?|years?\s*old|yrs?\s*old|yrs?|yo))?\b"""
    )
    private val TURNING_AGE_REGEX = Regex(
        """(?i)\b(?:(?:hon|han|hen|som|she|he|they|who)\s+)?(?:fyller|turns|turning|is\s+turning)\s+($AGE_TOKEN_PATTERN)(?:\s*(?:år(?:\s+gammal)?|years?\s*old|yrs?\s*old|yrs?|yo))?\b"""
    )
    private val CURRENT_AGE_WITH_UNIT_REGEX = Regex(
        """(?i)\b(?:(?:hon|han|hen|som|she|he|they|who)\s+)?(?:(?:är|is)\s+)?($AGE_TOKEN_PATTERN)\s+(?:år(?:\s+gammal)?|years?\s*old|yrs?\s*old|yo)\b"""
    )

    private val NUMBER_WORDS = mapOf(
        "a" to 1, "an" to 1, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
        "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11,
        "twelve" to 12, "couple" to 2,
        "en" to 1, "ett" to 1, "två" to 2, "tre" to 3, "fyra" to 4, "fem" to 5,
        "sex" to 6, "sju" to 7, "åtta" to 8, "nio" to 9, "tio" to 10, "elva" to 11, "tolv" to 12
    )
}
