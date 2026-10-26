package se.oscarb.bdai.agent.tools

import kotlinx.datetime.TimeZone
import se.oscarb.bdai.agent.domain.AgentTool
import se.oscarb.bdai.domain.BirthdayRepository
import kotlin.time.Clock

fun provideAgentTools(
    repository: BirthdayRepository,
    clock: Clock = Clock.System,
    timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() }
): List<AgentTool> = listOf(
    SearchPeopleTool(repository = repository, clock = clock, timeZone = timeZone),
    GetUpcomingBirthdaysTool(repository = repository, clock = clock, timeZone = timeZone),
    AddOrUpdatePersonTool(repository = repository, clock = clock, timeZone = timeZone),
    AddChildTool(repository = repository, clock = clock, timeZone = timeZone),
    DeletePersonTool(repository = repository, clock = clock, timeZone = timeZone)
)
