package se.oscarb.bdai.agent.tools

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import se.oscarb.bdai.agent.domain.ToolArguments
import se.oscarb.bdai.agent.domain.ToolException
import se.oscarb.bdai.data.DefaultBirthdayRepository
import se.oscarb.bdai.data.SampleBirthdays
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class BirthdayToolsTest {

    // Fixed date: 2026-10-26
    private val fixedClock = object : Clock {
        override fun now(): Instant = Instant.parse("2026-10-26T12:00:00Z")
    }
    private val utcZone = { TimeZone.UTC }

    @Test
    fun searchPeopleHandlesSwedishPossessiveSimonsAndReturnsChildrenAges() = runTest {
        val repository = DefaultBirthdayRepository(seedEntries = SampleBirthdays.defaultEntries())
        val searchTool = SearchPeopleTool(repository, clock = fixedClock, timeZone = utcZone)

        val result = searchTool.execute(ToolArguments(mapOf("query" to "Simons")))

        assertEquals(1, result["family_count"])
        val families = result["families"] as List<*>
        val simonFamily = families.single() as Map<*, *>
        assertEquals("Simon", simonFamily["name"])

        val children = simonFamily["children"] as List<*>
        assertEquals(2, children.size)

        val adam = children[0] as Map<*, *>
        assertEquals("Adam", adam["name"])
        assertEquals("2018-06-10", adam["birthday"])
        assertEquals(8, adam["age_now"])
        assertEquals(9, adam["turning_age"])

        val elsa = children[1] as Map<*, *>
        assertEquals("Elsa", elsa["name"])
        assertEquals("2021-02-14", elsa["birthday"])
        assertEquals(5, elsa["age_now"])
        assertEquals(6, elsa["turning_age"])
    }

    @Test
    fun searchPeopleReturnsBothFamiliesWhenTwoParentsShareSameName() = runTest {
        val repository = DefaultBirthdayRepository(seedEntries = SampleBirthdays.defaultEntries())
        val searchTool = SearchPeopleTool(repository, clock = fixedClock, timeZone = utcZone)

        val result = searchTool.execute(ToolArguments(mapOf("query" to "Alex's")))

        assertEquals(2, result["family_count"])
        assertEquals(true, result["has_multiple_matches_with_same_name"])

        val families = result["families"] as List<*>
        val firstAlex = families[0] as Map<*, *>
        val secondAlex = families[1] as Map<*, *>

        assertEquals("1984-03-12", firstAlex["birthday"])
        assertEquals("1992-07-25", secondAlex["birthday"])
    }

    @Test
    fun addChildThrowsDisambiguationExceptionWhenTwoParentsShareNameAndNoHintProvided() = runTest {
        val repository = DefaultBirthdayRepository(seedEntries = SampleBirthdays.defaultEntries())
        val addChildTool = AddChildTool(repository, clock = fixedClock, timeZone = utcZone)

        val exception = assertFailsWith<ToolException> {
            addChildTool.execute(
                ToolArguments(
                    mapOf(
                        "child_name" to "Hugo",
                        "child_birthday" to "idag",
                        "parent_name" to "Alex"
                    )
                )
            )
        }

        val message = exception.message.orEmpty()
        assertTrue(message.contains("Multiple parents named 'Alex'"))
        assertTrue(message.contains("1984-03-12"))
        assertTrue(message.contains("Leo"))
        assertTrue(message.contains("1992-07-25"))
        assertTrue(message.contains("Nora"))
    }

    @Test
    fun addChildSucceedsWhenDisambiguatedWithParentBirthdayHint() = runTest {
        val repository = DefaultBirthdayRepository(seedEntries = SampleBirthdays.defaultEntries())
        val addChildTool = AddChildTool(repository, clock = fixedClock, timeZone = utcZone)

        val result = addChildTool.execute(
            ToolArguments(
                mapOf(
                    "child_name" to "Hugo",
                    "child_birthday" to "idag",
                    "parent_name" to "Alex",
                    "parent_birthday_hint" to "1992-07-25"
                )
            )
        )

        assertEquals("saved", result["status"])
        assertEquals("2026-10-26", result["child_birthday"])

        val alexEntries = repository.getEntriesSnapshot().filter { it.name == "Alex" }
        val firstAlex = alexEntries.first { it.birthday == "1984-03-12" }
        val secondAlex = alexEntries.first { it.birthday == "1992-07-25" }

        assertEquals(listOf("Leo"), firstAlex.children.map { it.name })
        assertEquals(listOf("Nora", "Hugo"), secondAlex.children.map { it.name })
    }

    @Test
    fun getUpcomingBirthdaysReturnsSortedUpcomingWithinWindow() = runTest {
        val repository = DefaultBirthdayRepository(seedEntries = SampleBirthdays.defaultEntries())
        val upcomingTool = GetUpcomingBirthdaysTool(repository, clock = fixedClock, timeZone = utcZone)

        // On 2026-10-26: Lucas (2020-10-30 -> in 4 days), Bob (1990-11-03 -> in 8 days), Nora (2022-11-18 -> in 23 days)
        val result = upcomingTool.execute(ToolArguments(mapOf("days" to 10)))

        assertEquals(2, result["count"])
        val birthdays = result["birthdays"] as List<*>
        val first = birthdays[0] as Map<*, *>
        val second = birthdays[1] as Map<*, *>

        assertEquals("Lucas", first["name"])
        assertEquals(4, first["days_until_birthday"])
        assertEquals(5, first["age_now"])
        assertEquals(6, first["turning_age"])

        assertEquals("Bob", second["name"])
        assertEquals(8, second["days_until_birthday"])
        assertEquals(35, second["age_now"])
        assertEquals(36, second["turning_age"])
    }
}
