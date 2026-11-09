package se.oscarb.bdai.agent.tools

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import se.oscarb.bdai.agent.domain.AgentTool
import se.oscarb.bdai.agent.domain.ToolArguments
import se.oscarb.bdai.agent.domain.ToolException
import se.oscarb.bdai.agent.domain.ToolParameter
import se.oscarb.bdai.domain.BirthdayRepository
import kotlin.time.Clock

class AddOrUpdatePersonTool(
    private val repository: BirthdayRepository,
    private val clock: Clock = Clock.System,
    private val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() }
) : AgentTool {

    private val pendingYearByName = mutableMapOf<String, Int>()

    override val name = "add_or_update_person"

    override val description =
        "Adds a new top-level person or updates an existing person or child's birthday, birth year, age, friend_of tag, or partner details."

    override val parameters = listOf(
        ToolParameter(
            name = "name",
            type = ToolParameter.Type.STRING,
            description = "The person's or child's name."
        ),
        ToolParameter(
            name = "birthday",
            type = ToolParameter.Type.STRING,
            description = "Optional birthday, birth year (e.g. '2025' to change only the year of an existing person), or natural date/age phrase (e.g. '26 oktober', 'hon fyllde 1 den 26 oktober', '1988-04-15', 'idag').",
            isRequired = false
        ),
        ToolParameter(
            name = "age",
            type = ToolParameter.Type.INT,
            description = "Optional age in years if the user mentioned how old the person is or what age they turned (e.g. 'hon fyllde 1 den 26 oktober' -> age=1). Pass age here so the app subtracts the right number of years.",
            isRequired = false
        ),
        ToolParameter(
            name = "friend_of",
            type = ToolParameter.Type.STRING,
            description = "Optional tag indicating whose friend this person is (e.g. 'Me' or 'Partner').",
            isRequired = false
        ),
        ToolParameter(
            name = "partner_name",
            type = ToolParameter.Type.STRING,
            description = "Optional name of the person's partner.",
            isRequired = false
        ),
        ToolParameter(
            name = "partner_birthday",
            type = ToolParameter.Type.STRING,
            description = "Optional partner birthday in YYYY-MM-DD format or natural date expression.",
            isRequired = false
        )
    )

    override suspend fun execute(arguments: ToolArguments): Map<String, Any?> {
        val rawName = arguments.string("name")
            ?: throw ToolException("The 'name' parameter is required.")
        val today = clock.todayIn(timeZone())
        val ageHint = arguments.int("age")
            ?: arguments.string("age")?.toIntOrNull()

        val allEntries = repository.getEntriesSnapshot()
        val effectiveName = resolveExistingPersonName(allEntries, rawName) ?: rawName.trim()
        val existingBirthday = findExistingBirthday(allEntries, effectiveName)
        val nameKey = effectiveName.lowercase()
        val pendingYear = pendingYearByName[nameKey]

        // If a previous turn provided only a year (e.g. "2025") for a new person without a saved birthday,
        // use that pending year as the fallback birth year when the user provides the day & month on the next turn.
        val effectiveExistingBirthday = existingBirthday
            ?: pendingYear?.let { LocalDate(it, 1, 1) }

        val rawBirthday = arguments.string("birthday")
        val parsedBirthday = when {
            rawBirthday != null -> {
                val parsed = DateExpressions.parseBirthday(
                    input = rawBirthday,
                    today = today,
                    ageHint = ageHint,
                    existingBirthday = if (DateExpressions.extractYearOnly(rawBirthday, today) != null) {
                        existingBirthday
                    } else {
                        effectiveExistingBirthday
                    }
                )
                if (parsed == null) {
                    val yearOnly = DateExpressions.extractYearOnly(rawBirthday, today)
                    if (yearOnly != null) {
                        pendingYearByName[nameKey] = yearOnly
                        throw ToolException(
                            "Got birth year $yearOnly for '$rawName', but no day and month are saved yet. " +
                                "Please ask the user for the day and month (and then call add_or_update_person with '$yearOnly' and the day/month)."
                        )
                    }
                    throw ToolException(unknownDateMessage(rawBirthday))
                }
                pendingYearByName.remove(nameKey)
                parsed
            }
            ageHint != null && existingBirthday != null -> {
                DateExpressions.parseBirthday(
                    input = "",
                    today = today,
                    ageHint = ageHint,
                    existingBirthday = existingBirthday
                )
            }
            else -> null
        }

        val parsedPartnerBirthday = arguments.string("partner_birthday")?.let { raw ->
            val partnerExisting = arguments.string("partner_name")?.let { pName ->
                findExistingBirthday(allEntries, pName)
            }
            DateExpressions.parseBirthday(raw, today, existingBirthday = partnerExisting)
                ?: throw ToolException(unknownDateMessage(raw))
        }

        val savedEntry = repository.upsertPerson(
            name = effectiveName,
            birthday = parsedBirthday?.toString(),
            friendOf = arguments.string("friend_of"),
            partnerName = arguments.string("partner_name"),
            partnerBirthday = parsedPartnerBirthday?.toString()
        )

        val familyMap = savedEntry.toFamilyMap(today)
        val matchingPartnerMap = (familyMap["partner"] as? Map<*, *>)
            ?.takeIf { (it["name"] as? String).equals(effectiveName, ignoreCase = true) }
        val matchingChildMap = (familyMap["children"] as? List<*>)
            ?.mapNotNull { it as? Map<*, *> }
            ?.firstOrNull { (it["name"] as? String).equals(effectiveName, ignoreCase = true) }

        return buildMap {
            put("status", "saved")
            val targetPersonMap = matchingChildMap ?: matchingPartnerMap ?: familyMap
            val summarySv = targetPersonMap["info_sv"] ?: familyMap["info_sv"]
            val summaryEn = targetPersonMap["info_en"] ?: familyMap["info_en"]
            if (summarySv != null) put("summary_sv", summarySv)
            if (summaryEn != null) put("summary_en", summaryEn)
            put("person", targetPersonMap)
            if (targetPersonMap !== familyMap) {
                put("family", familyMap)
            }
        }
    }
}
