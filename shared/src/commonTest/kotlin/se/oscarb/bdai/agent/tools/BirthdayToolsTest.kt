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

    @Test
    fun searchPeopleSingleParentDoesNotDuplicateChildrenAsTopLevelPeopleAndProvidesCleanSummary() = runTest {
        val repository = DefaultBirthdayRepository(seedEntries = SampleBirthdays.defaultEntries())
        val searchTool = SearchPeopleTool(repository, clock = fixedClock, timeZone = utcZone)

        val result = searchTool.execute(ToolArguments(mapOf("query" to "Simon")))

        assertEquals(false, result["has_multiple_matches_with_same_name"])
        assertEquals(null, result["people"])
        val summary = result["summary"] as String
        assertTrue(summary.contains("Simon är 38 år (född 15 april 1988, fyller 39 år den 15 april om"), summary)
        assertTrue(summary.contains("There is only 1 Simon"))

        // Searching for a single child scopes the family's children to only that child and includes info_sv
        val childResult = searchTool.execute(ToolArguments(mapOf("query" to "Adam")))
        val childSummary = childResult["summary"] as String
        assertTrue(childSummary.contains("Adam är 8 år (född 10 juni 2018, fyller 9 år den 10 juni om"), childSummary)
        val childFamilies = childResult["families"] as List<*>
        val scopedFamily = childFamilies.single() as Map<*, *>
        val scopedChildren = scopedFamily["children"] as List<*>
        assertEquals(1, scopedChildren.size)
        val adamMap = scopedChildren.single() as Map<*, *>
        assertEquals("Adam", adamMap["name"])
        assertTrue((adamMap["info_sv"] as String).contains("fyller 9 år den 10 juni om"))
    }

    @Test
    fun addPersonAndChildSubtractYearsWhenGivenSwedishAgePhrasesOrAgeParam() = runTest {
        val novClock = object : Clock {
            override fun now(): Instant = Instant.parse("2026-11-08T12:00:00Z")
        }
        val repository = DefaultBirthdayRepository(seedEntries = SampleBirthdays.defaultEntries())
        val addPersonTool = AddOrUpdatePersonTool(repository, clock = novClock, timeZone = utcZone)
        val addChildTool = AddChildTool(repository, clock = novClock, timeZone = utcZone)

        // 1) Inline Swedish phrase: "hon fyllde 1 den 26 oktober" -> 2025-10-26
        val res1 = addPersonTool.execute(
            ToolArguments(
                mapOf(
                    "name" to "Ella",
                    "birthday" to "hon fyllde 1 den 26 oktober"
                )
            )
        )
        val ella = res1["person"] as Map<*, *>
        assertEquals("2025-10-26", ella["birthday"])
        assertEquals(1, ella["age_now"])

        // 2) Separate age parameter with month/day: birthday="26 oktober", age=1 -> 2025-10-26
        val res2 = addChildTool.execute(
            ToolArguments(
                mapOf(
                    "child_name" to "Lilly",
                    "child_birthday" to "26 oktober",
                    "age" to 1,
                    "parent_name" to "Simon"
                )
            )
        )
        assertEquals("2025-10-26", res2["child_birthday"])

        // 3) Model passed current year in ISO date ("2026-10-26") alongside age=1 -> still 2025-10-26
        val res3 = addChildTool.execute(
            ToolArguments(
                mapOf(
                    "child_name" to "Lilly",
                    "child_birthday" to "2026-10-26",
                    "age" to 1,
                    "parent_name" to "Simon"
                )
            )
        )
        assertEquals("2025-10-26", res3["child_birthday"])

        // 4) Swedish slash dates & "blev X år" & "förra året"
        val res4 = addPersonTool.execute(
            ToolArguments(
                mapOf(
                    "name" to "Astrid",
                    "birthday" to "hon blev 1 år den 26/10"
                )
            )
        )
        val astrid = res4["person"] as Map<*, *>
        assertEquals("2025-10-26", astrid["birthday"])

        val res5 = addPersonTool.execute(
            ToolArguments(
                mapOf(
                    "name" to "Nils",
                    "birthday" to "27/9 1987"
                )
            )
        )
        val nils = res5["person"] as Map<*, *>
        assertEquals("1987-09-27", nils["birthday"])
    }

    @Test
    fun searchByMonthAndExactNameAvoidsFalseSubstringMatches() = runTest {
        val repository = DefaultBirthdayRepository(seedEntries = SampleBirthdays.defaultEntries())
        val addPersonTool = AddOrUpdatePersonTool(repository, clock = fixedClock, timeZone = utcZone)
        val addChildTool = AddChildTool(repository, clock = fixedClock, timeZone = utcZone)
        val deleteTool = DeletePersonTool(repository, clock = fixedClock, timeZone = utcZone)
        val searchTool = SearchPeopleTool(repository, clock = fixedClock, timeZone = utcZone)

        // Add "Mats" and "Matilda" -> searching "Mats" must only return "Mats", not "Matilda"
        addPersonTool.execute(ToolArguments(mapOf("name" to "Mats", "birthday" to "1985-05-10")))
        addPersonTool.execute(ToolArguments(mapOf("name" to "Matilda", "birthday" to "1991-08-12")))

        val matsSearch = searchTool.execute(ToolArguments(mapOf("query" to "Mats")))
        assertEquals(1, matsSearch["family_count"])
        assertEquals(false, matsSearch["has_multiple_matches_with_same_name"])

        // Add and delete child under parent "Anders" (ending in 's')
        val childRes = addChildTool.execute(
            ToolArguments(
                mapOf(
                    "child_name" to "Vera",
                    "child_birthday" to "2022-04-03",
                    "parent_name" to "Anders"
                )
            )
        )
        val andersFamily = childRes["family"] as Map<*, *>
        assertEquals("Anders", andersFamily["name"])

        val delRes = deleteTool.execute(
            ToolArguments(
                mapOf(
                    "name" to "Vera",
                    "parent_name" to "Anders"
                )
            )
        )
        assertEquals("deleted", delRes["status"])

        // Month search ("oktober")
        val octSearch = searchTool.execute(ToolArguments(mapOf("query" to "oktober")))
        assertTrue((octSearch["summary"] as String).contains("Lucas"))
    }

    @Test
    fun partialBirthdayUpdateChangesOnlyYearOrOnlyDayMonthAndSupportsTwoTurnNewPerson() = runTest {
        val novClock = object : Clock {
            override fun now(): Instant = Instant.parse("2026-11-08T12:00:00Z")
        }
        val repository = DefaultBirthdayRepository(seedEntries = SampleBirthdays.defaultEntries())
        val addPersonTool = AddOrUpdatePersonTool(repository, clock = novClock, timeZone = utcZone)

        // Lucas starts at 2020-10-30 in SampleBirthdays
        // 1) Update only the birth year ("2025") -> keeps October 30, changes year to 2025 ("2025-10-30")
        val yearUpdateRes = addPersonTool.execute(
            ToolArguments(
                mapOf(
                    "name" to "Lucas",
                    "birthday" to "2025"
                )
            )
        )
        val updatedLucas1 = yearUpdateRes["person"] as Map<*, *>
        assertEquals("2025-10-30", updatedLucas1["birthday"])

        // 2) Update only the day & month ("26 oktober") -> preserves existing year 2025 ("2025-10-26")
        val dayMonthUpdateRes = addPersonTool.execute(
            ToolArguments(
                mapOf(
                    "name" to "Lucas",
                    "birthday" to "26 oktober"
                )
            )
        )
        val updatedLucas2 = dayMonthUpdateRes["person"] as Map<*, *>
        assertEquals("2025-10-26", updatedLucas2["birthday"])

        // 3) Brand-new person given only a year on turn 1 ("2025"), then day/month on turn 2 ("26 oktober")
        assertFailsWith<ToolException> {
            addPersonTool.execute(
                ToolArguments(
                    mapOf(
                        "name" to "Saga",
                        "birthday" to "2025"
                    )
                )
            )
        }
        val turn2Res = addPersonTool.execute(
            ToolArguments(
                mapOf(
                    "name" to "Saga",
                    "birthday" to "26 oktober"
                )
            )
        )
        val saga = turn2Res["person"] as Map<*, *>
        assertEquals("2025-10-26", saga["birthday"])
    }
}
