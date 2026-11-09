package se.oscarb.bdai.agent.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.format
import kotlinx.datetime.format.DayOfWeekNames
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.Padding
import kotlinx.datetime.format.char

private val spokenDateFormat = LocalDate.Format {
    dayOfWeek(DayOfWeekNames.ENGLISH_FULL)
    chars(", ")
    monthName(MonthNames.ENGLISH_FULL)
    char(' ')
    day(padding = Padding.NONE)
    chars(", ")
    year()
}

/**
 * Formats a date like "Friday, October 23, 2026". Tool results use this form so the model can
 * repeat the weekday and date without calculating it mentally.
 */
fun LocalDate.toSpokenDate(): String = format(spokenDateFormat)

/**
 * Formats a birth date in natural Swedish day-month-year order, e.g. "27 september 1987".
 */
fun LocalDate.toSwedishDate(): String = "${day} ${month.toSwedishName()} $year"

/**
 * Formats day and month in Swedish, e.g. "27 september".
 */
fun LocalDate.toSwedishDayMonth(): String = "${day} ${month.toSwedishName()}"

/**
 * Formats a birth date in clear day-month-year English order, e.g. "27 September 1987".
 */
fun LocalDate.toEnglishDate(): String = "${day} ${month.toEnglishName()} $year"

/**
 * Formats day and month in English, e.g. "27 September".
 */
fun LocalDate.toEnglishDayMonth(): String = "${day} ${month.toEnglishName()}"

fun Month.toSwedishName(): String = when (this) {
    Month.JANUARY -> "januari"
    Month.FEBRUARY -> "februari"
    Month.MARCH -> "mars"
    Month.APRIL -> "april"
    Month.MAY -> "maj"
    Month.JUNE -> "juni"
    Month.JULY -> "juli"
    Month.AUGUST -> "augusti"
    Month.SEPTEMBER -> "september"
    Month.OCTOBER -> "oktober"
    Month.NOVEMBER -> "november"
    Month.DECEMBER -> "december"
}

fun Month.toEnglishName(): String = when (this) {
    Month.JANUARY -> "January"
    Month.FEBRUARY -> "February"
    Month.MARCH -> "March"
    Month.APRIL -> "April"
    Month.MAY -> "May"
    Month.JUNE -> "June"
    Month.JULY -> "July"
    Month.AUGUST -> "August"
    Month.SEPTEMBER -> "September"
    Month.OCTOBER -> "October"
    Month.NOVEMBER -> "November"
    Month.DECEMBER -> "December"
}
