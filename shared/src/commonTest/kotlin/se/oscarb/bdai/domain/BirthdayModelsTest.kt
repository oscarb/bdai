package se.oscarb.bdai.domain

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BirthdayModelsTest {

    @Test
    fun flattenBirthdaysComputesExactAgesAndCountdowns() {
        val today = LocalDate(2026, 6, 1)
        val entries = listOf(
            PersonEntry(
                name = "Simon",
                birthday = "1988-04-15", // Already had birthday in 2026 -> ageNow = 38, turningAge = 39
                friendOf = "Me",
                partner = PartnerEntry(
                    name = "Emma",
                    birthday = "1989-06-01" // Birthday is today -> ageNow = 37, turningAge = 37, daysUntil = 0
                ),
                children = listOf(
                    ChildEntry(
                        name = "Adam",
                        birthday = "2018-06-10" // Birthday in 9 days -> ageNow = 7, turningAge = 8, daysUntil = 9
                    ),
                    ChildEntry(
                        name = "Elsa",
                        birthday = "2021-02-14" // Already had birthday in 2026 -> ageNow = 5, turningAge = 6
                    )
                )
            )
        )

        val flattened = entries.flattenBirthdays(today)
        assertEquals(4, flattened.size)

        // Sorted by daysUntilNextBirthday ascending: Emma (0), Adam (9), Elsa, Simon
        val emma = flattened[0]
        assertEquals("Emma", emma.name)
        assertEquals(0, emma.daysUntilNextBirthday)
        assertEquals(37, emma.ageNow)
        assertEquals(37, emma.turningAge)
        assertEquals(LocalDate(2026, 6, 1), emma.nextBirthday)
        assertEquals("Simon", emma.partnerName)
        assertEquals(listOf("Adam", "Elsa"), emma.childNames)

        val adam = flattened[1]
        assertEquals("Adam", adam.name)
        assertEquals(9, adam.daysUntilNextBirthday)
        assertEquals(7, adam.ageNow)
        assertEquals(8, adam.turningAge)
        assertEquals(LocalDate(2026, 6, 10), adam.nextBirthday)
        assertEquals(listOf("Simon", "Emma"), adam.parentNames)
        assertEquals("Me", adam.friendOf)

        val simon = flattened.first { it.name == "Simon" }
        assertEquals(38, simon.ageNow)
        assertEquals(39, simon.turningAge)
        assertEquals(LocalDate(2027, 4, 15), simon.nextBirthday)
        assertTrue(simon.daysUntilNextBirthday > 300)
    }

    @Test
    fun flattenBirthdaysHandlesLeapYearBirthdayOnNonLeapYear() {
        val today = LocalDate(2026, 2, 20)
        val entries = listOf(
            PersonEntry(
                name = "Viktor",
                birthday = "2020-02-29",
                friendOf = "Partner"
            )
        )

        val flattened = entries.flattenBirthdays(today)
        assertEquals(1, flattened.size)
        val viktor = flattened.single()
        assertEquals(LocalDate(2026, 2, 28), viktor.nextBirthday)
        assertEquals(8, viktor.daysUntilNextBirthday)
        assertEquals(5, viktor.ageNow)
        assertEquals(6, viktor.turningAge)
    }

    @Test
    fun birthdayYamlFormatParsesAndSerializesRoundTripWithPartnerAndTwoSpaces() {
        val sampleYaml = """
            - Simon: 1988-04-15
              +Emma: 1990-08-22
              Adam: 2018-06-10
              Elsa: 2021-02-14

            # Me
            - Alex: 1984-03-12
              Leo: 2016-05-04
            - Alex: 1992-07-25
              Nora: 2020-11-18
            - ParentWithoutBirthday:
              Kid: 2019-01-08
        """.trimIndent() + "\n"

        val parsed = BirthdayYamlFormat.parse(sampleYaml)
        assertEquals(4, parsed.size)
        assertEquals("Simon", parsed[0].name)
        assertEquals("Emma", parsed[0].partner?.name)
        assertEquals("1990-08-22", parsed[0].partner?.birthday)
        assertEquals(listOf("Adam", "Elsa"), parsed[0].children.map { it.name })

        assertEquals("Me", parsed[1].friendOf)
        assertEquals("1984-03-12", parsed[1].birthday)
        assertEquals(listOf("Leo"), parsed[1].children.map { it.name })

        assertEquals("Me", parsed[2].friendOf)
        assertEquals("1992-07-25", parsed[2].birthday)
        assertEquals(listOf("Nora"), parsed[2].children.map { it.name })

        assertEquals(null, parsed[3].birthday)
        assertEquals(listOf("Kid"), parsed[3].children.map { it.name })

        val serialized = BirthdayYamlFormat.serialize(parsed)
        assertEquals(sampleYaml, serialized)
    }
}
