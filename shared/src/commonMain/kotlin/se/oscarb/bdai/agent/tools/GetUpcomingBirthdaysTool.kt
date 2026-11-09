package se.oscarb.bdai.agent.tools

import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import se.oscarb.bdai.agent.domain.AgentTool
import se.oscarb.bdai.agent.domain.ToolArguments
import se.oscarb.bdai.agent.domain.ToolParameter
import se.oscarb.bdai.agent.domain.toSwedishDate
import se.oscarb.bdai.domain.BirthdayRepository
import se.oscarb.bdai.domain.flattenBirthdays
import kotlin.time.Clock

class GetUpcomingBirthdaysTool(
    private val repository: BirthdayRepository,
    private val clock: Clock = Clock.System,
    private val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() }
) : AgentTool {

    override val name = "get_upcoming_birthdays"

    override val description =
        "Returns upcoming birthdays within the next N days (default 30, or pass 7 for the next week, or pass a month name in 'period'), " +
            "sorted by days_until_birthday ascending, with pre-formatted 'info_sv' and 'info_en' sentences."

    override val parameters = listOf(
        ToolParameter(
            name = "days",
            type = ToolParameter.Type.INT,
            description = "Maximum number of days ahead to include (defaults to 30; pass 7 for next week).",
            isRequired = false
        ),
        ToolParameter(
            name = "period",
            type = ToolParameter.Type.STRING,
            description = "Optional time window or month phrase such as 'this week', 'next month', 'nästa vecka', or 'oktober'.",
            isRequired = false
        )
    )

    override suspend fun execute(arguments: ToolArguments): Map<String, Any?> {
        val today = clock.todayIn(timeZone())
        val periodStr = arguments.string("period")
        val allSorted = repository.getEntriesSnapshot().flattenBirthdays(today)

        val queriedMonth = DateExpressions.parseMonthFromText(periodStr)
        if (queriedMonth != null) {
            val monthMatches = allSorted.filter { it.birthday.month == queriedMonth }
            val mapped = monthMatches.map { it.toToolMap() }
            val summarySv = if (monthMatches.isEmpty()) {
                "Inga födelsedagar i ${queriedMonth.name.lowercase()}."
            } else {
                monthMatches.joinToString(" | ") { it.toInfoSv() }
            }
            return mapOf(
                "today" to today.toString(),
                "today_sv" to today.toSwedishDate(),
                "month" to queriedMonth.name,
                "count" to mapped.size,
                "summary_sv" to summarySv,
                "birthdays" to mapped
            )
        }

        val requestedDays = arguments.int("days")
            ?: DateExpressions.parseDaysLookahead(arguments.string("days"))
            ?: DateExpressions.parseDaysLookahead(periodStr)
            ?: DEFAULT_DAYS
        val maxDays = requestedDays.coerceIn(0, 366)

        val upcomingFlat = allSorted.filter { it.daysUntilNextBirthday <= maxDays }
        // If no birthdays fall within the requested window (e.g. days=7 when next is in 14 days),
        // include the next 5 upcoming birthdays so the model can still answer "Who is next?" or "And after that?".
        val effectiveFlat = upcomingFlat.ifEmpty { allSorted.take(5) }
        val upcoming = effectiveFlat.map { it.toToolMap() }

        val nextOverall = allSorted.firstOrNull()
        val nextGroup = if (nextOverall != null) {
            allSorted.filter { it.daysUntilNextBirthday == nextOverall.daysUntilNextBirthday }
        } else {
            emptyList()
        }

        val nextBirthdaySv = nextGroup.joinToString(" ") { it.toUpcomingSv() }
        val nextBirthdayEn = nextGroup.joinToString(" ") { it.toUpcomingEn() }

        val summarySv = when {
            upcomingFlat.isNotEmpty() ->
                upcomingFlat.joinToString(" | ") { it.toUpcomingSv() }
            effectiveFlat.isNotEmpty() ->
                "Inga födelsedagar inom $maxDays dagar. Närmaste kommande: " +
                    effectiveFlat.joinToString(" | ") { it.toUpcomingSv() }
            else ->
                "Inga födelsedagar sparade."
        }

        return buildMap {
            put("today", today.toString())
            put("today_sv", today.toSwedishDate())
            put("days", maxDays)
            put("count", upcomingFlat.size)
            if (nextBirthdaySv.isNotEmpty()) {
                put("next_birthday_sv", nextBirthdaySv)
                put("next_birthday_en", nextBirthdayEn)
            }
            put("summary_sv", summarySv)
            put("birthdays", upcoming)
        }
    }

    private companion object {
        const val DEFAULT_DAYS = 30
    }
}
