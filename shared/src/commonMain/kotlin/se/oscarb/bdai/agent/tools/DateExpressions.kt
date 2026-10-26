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
        "'1988-04-15', '2021-02-14', 'today', 'yesterday', 'tomorrow', '15 april 1988', 'may 12 2019', 'idag', 'igår'"

    fun parseBirthday(input: String, today: LocalDate): LocalDate? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        if (ISO_DATE.matches(trimmed)) {
            return runCatching { LocalDate.parse(trimmed) }.getOrNull()
        }
        val words = words(trimmed)
        if (words.isEmpty()) return null
        return parseIsoDate(words)
            ?: parseRelativeDate(words, today)
            ?: parseCalendarDate(words, today, preferPastOrCurrentYear = true)
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

    private fun parseCalendarDate(
        words: List<String>,
        today: LocalDate,
        preferPastOrCurrentYear: Boolean
    ): LocalDate? {
        val dateWords = words.filter { it !in setOf("of", "den") }
        val month = dateWords.firstNotNullOfOrNull { parseMonth(it) } ?: return null
        val numbers = dateWords.mapNotNull { word ->
            ORDINAL.matchEntire(word)?.groupValues?.get(1)?.toIntOrNull()
        }
        val unknownWords = dateWords.filter { parseMonth(it) == null && !ORDINAL.matches(it) }
        if (unknownWords.isNotEmpty()) return null

        return when (numbers.size) {
            1 -> {
                val day = numbers.single()
                if (preferPastOrCurrentYear) {
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
    private val ORDINAL = Regex("""(\d{1,4})(st|nd|rd|th|:e|:a|e|a)?""")
    private val PUNCTUATION = Regex("""[,.!?;:()"']""")
    private val A_COUPLE = Regex("""\ba couple\b""")
    private val FILLER_WORDS = setOf("on", "the", "due", "at", "born", "född", "födda")
    private val NUMBER_WORDS = mapOf(
        "a" to 1, "an" to 1, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
        "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11,
        "twelve" to 12, "couple" to 2,
        "en" to 1, "ett" to 1, "två" to 2, "tre" to 3, "fyra" to 4, "fem" to 5,
        "sex" to 6, "sju" to 7, "åtta" to 8, "nio" to 9, "tio" to 10
    )
}
