package se.oscarb.bdai.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.serialization.Serializable

/**
 * Slim model representing a person/family in `birthdays.txt` on the NAS.
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
 * Parser and serializer for the canonical 2-space indented `birthdays.yaml` format:
 *
 * ```yaml
 * - Simon: 1988-04-15
 *   +Emma: 1990-08-22
 *   Adam: 2018-06-10
 *   Elsa: 2021-02-14
 *
 * # Me
 * - Bob: 1985-11-03
 *   Lucas: 2016-09-19
 * ```
 */
object BirthdayYamlFormat {

    fun parse(text: String): List<PersonEntry> {
        val entries = mutableListOf<PersonEntry>()
        if (text.isBlank()) return entries

        var currentFriendOf: String? = null

        for (rawLine in text.lineSequence()) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) continue

            if (trimmed.startsWith("#")) {
                val section = trimmed.removePrefix("#").trim()
                currentFriendOf = section.ifEmpty { null }
                continue
            }

            val isListItem = trimmed.startsWith("-")
            val isIndented = !isListItem && (rawLine.startsWith(" ") || rawLine.startsWith("\t"))
            val content = if (isListItem) trimmed.removePrefix("-").trim() else trimmed

            val colonIndex = content.indexOf(':')
            val rawName = if (colonIndex >= 0) content.substring(0, colonIndex).trim() else content
            val rawDate = if (colonIndex >= 0) content.substring(colonIndex + 1).trim() else ""

            if (rawName.startsWith("+")) {
                val partnerName = rawName.removePrefix("+").trim()
                if (entries.isNotEmpty() && partnerName.isNotEmpty()) {
                    val lastIdx = entries.lastIndex
                    entries[lastIdx] = entries[lastIdx].copy(
                        partner = PartnerEntry(
                            name = partnerName,
                            birthday = rawDate.ifEmpty { null }
                        )
                    )
                }
                continue
            }

            if (isIndented && entries.isNotEmpty()) {
                if (rawName.isNotEmpty() && rawDate.isNotEmpty()) {
                    val lastIdx = entries.lastIndex
                    val current = entries[lastIdx]
                    entries[lastIdx] = current.copy(
                        children = current.children + ChildEntry(
                            name = rawName,
                            birthday = rawDate
                        )
                    )
                }
                continue
            }

            if (rawName.isNotEmpty()) {
                entries += PersonEntry(
                    name = rawName,
                    birthday = rawDate.ifEmpty { null },
                    friendOf = currentFriendOf
                )
            }
        }

        return entries
    }

    fun serialize(entries: List<PersonEntry>): String {
        if (entries.isEmpty()) return ""

        val orderedGroups = linkedMapOf<String, MutableList<PersonEntry>>()
        orderedGroups[""] = mutableListOf()
        for (entry in entries) {
            if (entry.name.isBlank()) continue
            val group = entry.friendOf?.trim().orEmpty()
            orderedGroups.getOrPut(group) { mutableListOf() }.add(entry)
        }

        val lines = mutableListOf<String>()
        for ((group, groupEntries) in orderedGroups) {
            if (groupEntries.isEmpty()) continue
            if (group.isNotEmpty()) {
                if (lines.isNotEmpty()) {
                    lines += ""
                }
                lines += "# $group"
            }

            for (entry in groupEntries) {
                val name = entry.name.trim()
                val bday = entry.birthday?.trim().orEmpty()
                lines += if (bday.isNotEmpty()) "- $name: $bday" else "- $name:"

                entry.partner?.takeIf { it.name.isNotBlank() }?.let { partner ->
                    val pName = partner.name.trim()
                    val pBday = partner.birthday?.trim().orEmpty()
                    lines += if (pBday.isNotEmpty()) "  +$pName: $pBday" else "  +$pName:"
                }

                for (child in entry.children) {
                    if (child.name.isNotBlank()) {
                        val cName = child.name.trim()
                        val cBday = child.birthday.trim()
                        lines += if (cBday.isNotEmpty()) "  $cName: $cBday" else "  $cName:"
                    }
                }
            }
        }

        return lines.joinToString("\n") + "\n"
    }
}

typealias BirthdayTxtFormat = BirthdayYamlFormat

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
