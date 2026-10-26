package se.oscarb.bdai.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.serialization.Serializable

/**
 * Slim, human-editable JSON schema used in `birthdays.json` on the NAS.
 * No UUIDs or clutter—easy to read and edit by hand.
 */
@Serializable
data class PersonEntry(
    val name: String,
    val birthday: String? = null,
    val friendOf: String? = null,
    val partner: PartnerEntry? = null,
    val children: List<ChildEntry> = emptyList()
)

@Serializable
data class PartnerEntry(
    val name: String,
    val birthday: String? = null
)

@Serializable
data class ChildEntry(
    val name: String,
    val birthday: String
)

/**
 * Computed view of an individual with a valid birthday (whether top-level, partner, or child),
 * including exact ages and days until next birthday computed in Kotlin.
 */
data class FlatBirthdayPerson(
    val key: String,
    val name: String,
    val birthday: LocalDate,
    val friendOf: String?,
    val parentNames: List<String>,
    val childNames: List<String>,
    val partnerName: String?,
    val ageNow: Int,
    val turningAge: Int,
    val nextBirthday: LocalDate,
    val daysUntilNextBirthday: Int
)

/**
 * Settings stored locally on the device.
 */
@Serializable
data class BdaiSettings(
    val nasUrl: String = "http://192.168.1.100/birthday-calendar/api.php",
    val notificationsEnabled: Boolean = true,
    val notifyDaysBefore: Boolean = true,
    val lastSyncEpochMillis: Long? = null,
    val hasPendingLocalChanges: Boolean = false
)

sealed interface SyncResult {
    data class Success(val count: Int) : SyncResult
    data class Error(val message: String) : SyncResult
}

interface BirthdayRepository {
    val entries: Flow<List<PersonEntry>>
    val settings: Flow<BdaiSettings>

    suspend fun getEntriesSnapshot(): List<PersonEntry>
    suspend fun replaceAllEntries(entries: List<PersonEntry>, markPendingSync: Boolean = true)

    suspend fun upsertPerson(
        name: String,
        birthday: String?,
        friendOf: String?,
        partnerName: String? = null,
        partnerBirthday: String? = null
    ): PersonEntry

    suspend fun addOrUpdateChild(
        parentName: String,
        parentBirthdayHint: String? = null,
        childName: String,
        childBirthday: String,
        friendOfIfNewParent: String? = null
    ): PersonEntry

    suspend fun deletePersonOrChild(
        name: String,
        birthdayHint: String? = null,
        parentNameHint: String? = null
    ): Boolean

    suspend fun updateSettings(transform: (BdaiSettings) -> BdaiSettings)
    suspend fun syncWithNas(): SyncResult
    suspend fun pullFromNas(): SyncResult
}

/**
 * Pure Kotlin helper that flattens [PersonEntry] trees into [FlatBirthdayPerson] items with
 * accurate ages and next-birthday countdowns relative to [today].
 */
fun List<PersonEntry>.flattenBirthdays(today: LocalDate): List<FlatBirthdayPerson> {
    val result = mutableListOf<FlatBirthdayPerson>()

    for (entry in this) {
        val parentList = buildList {
            add(entry.name)
            entry.partner?.name?.let { add(it) }
        }
        val childNames = entry.children.map { it.name }

        entry.birthday?.toLocalDateOrNull()?.let { bday ->
            result += createFlatPerson(
                key = "person:${entry.name}:${entry.birthday}",
                name = entry.name,
                birthday = bday,
                friendOf = entry.friendOf,
                parentNames = emptyList(),
                childNames = childNames,
                partnerName = entry.partner?.name,
                today = today
            )
        }

        entry.partner?.let { partner ->
            partner.birthday?.toLocalDateOrNull()?.let { bday ->
                result += createFlatPerson(
                    key = "partner:${partner.name}:${partner.birthday}",
                    name = partner.name,
                    birthday = bday,
                    friendOf = entry.friendOf,
                    parentNames = emptyList(),
                    childNames = childNames,
                    partnerName = entry.name,
                    today = today
                )
            }
        }

        for (child in entry.children) {
            child.birthday.toLocalDateOrNull()?.let { bday ->
                result += createFlatPerson(
                    key = "child:${entry.name}:${child.name}:${child.birthday}",
                    name = child.name,
                    birthday = bday,
                    friendOf = entry.friendOf,
                    parentNames = parentList,
                    childNames = emptyList(),
                    partnerName = null,
                    today = today
                )
            }
        }
    }

    return result.sortedWith(
        compareBy<FlatBirthdayPerson> { it.daysUntilNextBirthday }
            .thenBy { it.name.lowercase() }
    )
}

fun String.toLocalDateOrNull(): LocalDate? = runCatching { LocalDate.parse(this.trim()) }.getOrNull()

fun createFlatPerson(
    key: String,
    name: String,
    birthday: LocalDate,
    friendOf: String?,
    parentNames: List<String>,
    childNames: List<String>,
    partnerName: String?,
    today: LocalDate
): FlatBirthdayPerson {
    val birthdayThisYear = safeBirthdayInYear(birthday, today.year)
    val nextBirthday = if (birthdayThisYear >= today) {
        birthdayThisYear
    } else {
        safeBirthdayInYear(birthday, today.year + 1)
    }
    val daysUntil = today.daysUntil(nextBirthday)
    val turningAge = nextBirthday.year - birthday.year
    val ageNow = if (birthdayThisYear <= today) {
        today.year - birthday.year
    } else {
        today.year - birthday.year - 1
    }.coerceAtLeast(0)

    return FlatBirthdayPerson(
        key = key,
        name = name,
        birthday = birthday,
        friendOf = friendOf,
        parentNames = parentNames,
        childNames = childNames,
        partnerName = partnerName,
        ageNow = ageNow,
        turningAge = turningAge,
        nextBirthday = nextBirthday,
        daysUntilNextBirthday = daysUntil
    )
}

private fun safeBirthdayInYear(birthday: LocalDate, year: Int): LocalDate {
    return runCatching { LocalDate(year, birthday.month, birthday.day) }
        .getOrElse {
            // Feb 29 on non-leap year -> Feb 28
            LocalDate(year, birthday.month, 28)
        }
}
