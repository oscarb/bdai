package se.oscarb.bdai.data

import se.oscarb.bdai.domain.ChildEntry
import se.oscarb.bdai.domain.PartnerEntry
import se.oscarb.bdai.domain.PersonEntry

/**
 * Fictional sample entries used to seed the database on first launch when empty.
 */
object SampleBirthdays {
    fun defaultEntries(): List<PersonEntry> = listOf(
        PersonEntry(
            name = "Simon",
            birthday = "1988-04-15",
            friendOf = "Me",
            partner = PartnerEntry(
                name = "Emma",
                birthday = "1989-09-20"
            ),
            children = listOf(
                ChildEntry(name = "Adam", birthday = "2018-06-10"),
                ChildEntry(name = "Elsa", birthday = "2021-02-14")
            )
        ),
        PersonEntry(
            name = "Bob",
            birthday = "1990-11-03",
            friendOf = "Me",
            partner = PartnerEntry(
                name = "Maja",
                birthday = "1991-07-22"
            ),
            children = listOf(
                ChildEntry(name = "Lucas", birthday = "2020-10-30")
            )
        ),
        PersonEntry(
            name = "Alice",
            birthday = "1985-12-08",
            friendOf = "Partner",
            children = listOf(
                ChildEntry(name = "Viktor", birthday = "2016-05-19")
            )
        ),
        PersonEntry(
            name = "Alex",
            birthday = "1984-03-12",
            friendOf = "Me",
            children = listOf(
                ChildEntry(name = "Leo", birthday = "2015-08-04")
            )
        ),
        PersonEntry(
            name = "Alex",
            birthday = "1992-07-25",
            friendOf = "Partner",
            children = listOf(
                ChildEntry(name = "Nora", birthday = "2022-11-18")
            )
        )
    )
}
