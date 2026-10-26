package se.oscarb.bdai.agent.tools

import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import se.oscarb.bdai.agent.domain.AgentTool
import se.oscarb.bdai.agent.domain.ToolArguments
import se.oscarb.bdai.agent.domain.ToolParameter
import se.oscarb.bdai.agent.domain.toSpokenDate
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
        "Returns upcoming birthdays within the next N days (default 30, or pass 7 for the next week), " +
            "sorted by days_until_birthday ascending, with exact age_now, turning_age, next_birthday, and days_until_birthday."

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
            description = "Optional time window phrase such as 'this week', 'next week', 'this month', or 'nästa vecka'.",
            isRequired = false
        )
    )

    override suspend fun execute(arguments: ToolArguments): Map<String, Any?> {
        val today = clock.todayIn(timeZone())
        val requestedDays = arguments.int("days")
            ?: DateExpressions.parseDaysLookahead(arguments.string("days"))
            ?: DateExpressions.parseDaysLookahead(arguments.string("period"))
            ?: DEFAULT_DAYS
        val maxDays = requestedDays.coerceIn(0, 366)

        val upcoming = repository.getEntriesSnapshot()
            .flattenBirthdays(today)
            .filter { it.daysUntilNextBirthday <= maxDays }
            .map { it.toToolMap() }

        return mapOf(
            "today" to today.toString(),
            "spoken_today" to today.toSpokenDate(),
            "days" to maxDays,
            "count" to upcoming.size,
            "birthdays" to upcoming
        )
    }

    private companion object {
        const val DEFAULT_DAYS = 30
    }
}
