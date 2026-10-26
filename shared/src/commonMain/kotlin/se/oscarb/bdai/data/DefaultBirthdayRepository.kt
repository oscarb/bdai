package se.oscarb.bdai.data

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import se.oscarb.bdai.domain.BdaiSettings
import se.oscarb.bdai.domain.BirthdayRepository
import se.oscarb.bdai.domain.BirthdayTxtFormat
import se.oscarb.bdai.domain.ChildEntry
import se.oscarb.bdai.domain.PartnerEntry
import se.oscarb.bdai.domain.PersonEntry
import se.oscarb.bdai.domain.SyncResult
import se.oscarb.bdai.domain.toLocalDateOrNull
import kotlin.time.Clock

class DefaultBirthdayRepository(
    private val dao: BirthdayDao? = null,
    private val httpClient: HttpClient = HttpClient(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val seedEntries: List<PersonEntry> = SampleBirthdays.defaultEntries(),
    private val clock: Clock = Clock.System
) : BirthdayRepository {

    constructor(
        database: BirthdayDatabase,
        httpClient: HttpClient = HttpClient(),
        scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    ) : this(
        dao = database.birthdayDao,
        httpClient = httpClient,
        scope = scope
    )

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    private val mutex = Mutex()
    private var isInitialized = dao == null

    private val _entries = MutableStateFlow(seedEntries)
    private val _settings = MutableStateFlow(BdaiSettings())

    override val entries: Flow<List<PersonEntry>> = _entries.asStateFlow()
    override val settings: Flow<BdaiSettings> = _settings.asStateFlow()

    init {
        if (dao != null) {
            scope.launch {
                ensureInitialized()
                dao.observeStorage().filterNotNull().collect { stored ->
                    _entries.value = decodeEntries(stored.entriesJson)
                    _settings.value = decodeSettings(stored.settingsJson)
                }
            }
        }
    }

    private suspend fun ensureInitialized() {
        if (isInitialized || dao == null) return
        mutex.withLock {
            if (isInitialized) return
            val existing = dao.getStorage()
            if (existing == null) {
                val initialEntries = seedEntries
                val initialSettings = BdaiSettings()
                _entries.value = initialEntries
                _settings.value = initialSettings
                persistLocked(initialEntries, initialSettings)
            } else {
                val loadedEntries = decodeEntries(existing.entriesJson).ifEmpty { seedEntries }
                val loadedSettings = decodeSettings(existing.settingsJson)
                _entries.value = loadedEntries
                _settings.value = loadedSettings
                if (decodeEntries(existing.entriesJson).isEmpty() && seedEntries.isNotEmpty()) {
                    persistLocked(loadedEntries, loadedSettings)
                }
            }
            isInitialized = true
        }
    }

    override suspend fun getEntriesSnapshot(): List<PersonEntry> {
        ensureInitialized()
        return _entries.value
    }

    override suspend fun replaceAllEntries(entries: List<PersonEntry>, markPendingSync: Boolean) {
        ensureInitialized()
        mutex.withLock {
            val updatedSettings = _settings.value.copy(
                hasPendingLocalChanges = if (markPendingSync) true else _settings.value.hasPendingLocalChanges
            )
            _entries.value = entries
            _settings.value = updatedSettings
            persistLocked(entries, updatedSettings)
        }
        if (markPendingSync) {
            triggerBackgroundSync()
        }
    }

    override suspend fun upsertPerson(
        name: String,
        birthday: String?,
        friendOf: String?,
        partnerName: String?,
        partnerBirthday: String?
    ): PersonEntry {
        ensureInitialized()
        val trimmedName = name.trim()
        val normalizedBirthday = birthday?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedFriendOf = friendOf?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedPartnerName = partnerName?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedPartnerBirthday = partnerBirthday?.trim()?.takeIf { it.isNotEmpty() }

        val savedEntry = mutex.withLock {
            val current = _entries.value.toMutableList()
            val matchingIndices = current.mapIndexedNotNull { index, entry ->
                index.takeIf { entry.name.equals(trimmedName, ignoreCase = true) }
            }

            val targetIndex = when {
                matchingIndices.isEmpty() -> -1
                normalizedBirthday != null -> {
                    matchingIndices.firstOrNull { current[it].birthday == normalizedBirthday }
                        ?: matchingIndices.singleOrNull()
                        ?: -1
                }
                else -> matchingIndices.first()
            }

            val resultEntry = if (targetIndex >= 0) {
                val existing = current[targetIndex]
                val updatedPartner = when {
                    normalizedPartnerName != null -> PartnerEntry(
                        name = normalizedPartnerName,
                        birthday = normalizedPartnerBirthday
                            ?: existing.partner?.takeIf { it.name.equals(normalizedPartnerName, ignoreCase = true) }?.birthday
                    )
                    normalizedPartnerBirthday != null && existing.partner != null ->
                        existing.partner.copy(birthday = normalizedPartnerBirthday)
                    else -> existing.partner
                }
                existing.copy(
                    name = existing.name,
                    birthday = normalizedBirthday ?: existing.birthday,
                    friendOf = normalizedFriendOf ?: existing.friendOf,
                    partner = updatedPartner
                ).also { current[targetIndex] = it }
            } else {
                val partner = normalizedPartnerName?.let {
                    PartnerEntry(name = it, birthday = normalizedPartnerBirthday)
                }
                PersonEntry(
                    name = trimmedName,
                    birthday = normalizedBirthday,
                    friendOf = normalizedFriendOf,
                    partner = partner,
                    children = emptyList()
                ).also { current.add(it) }
            }

            val updatedSettings = _settings.value.copy(hasPendingLocalChanges = true)
            _entries.value = current
            _settings.value = updatedSettings
            persistLocked(current, updatedSettings)
            resultEntry
        }

        triggerBackgroundSync()
        return savedEntry
    }

    override suspend fun addOrUpdateChild(
        parentName: String,
        parentBirthdayHint: String?,
        childName: String,
        childBirthday: String,
        friendOfIfNewParent: String?
    ): PersonEntry {
        ensureInitialized()
        val trimmedParentName = parentName.trim()
        val trimmedHint = parentBirthdayHint?.trim()?.takeIf { it.isNotEmpty() }
        val trimmedChildName = childName.trim()
        val trimmedChildBirthday = childBirthday.trim()
        val trimmedFriendOf = friendOfIfNewParent?.trim()?.takeIf { it.isNotEmpty() }

        val updatedParent = mutex.withLock {
            val current = _entries.value.toMutableList()
            val candidateIndices = current.mapIndexedNotNull { index, entry ->
                val matchesName = entry.name.equals(trimmedParentName, ignoreCase = true) ||
                    entry.partner?.name?.equals(trimmedParentName, ignoreCase = true) == true
                index.takeIf { matchesName }
            }

            val targetIndex = when {
                candidateIndices.isEmpty() -> -1
                trimmedHint != null -> {
                    candidateIndices.firstOrNull { idx ->
                        val entry = current[idx]
                        entry.birthday.equals(trimmedHint, ignoreCase = true) ||
                            entry.birthday?.startsWith(trimmedHint) == true ||
                            entry.partner?.birthday.equals(trimmedHint, ignoreCase = true) ||
                            entry.partner?.birthday?.startsWith(trimmedHint) == true ||
                            entry.children.any { it.name.equals(trimmedHint, ignoreCase = true) }
                    } ?: candidateIndices.singleOrNull() ?: -1
                }
                else -> candidateIndices.first()
            }

            val resultEntry = if (targetIndex >= 0) {
                val existing = current[targetIndex]
                val updatedChildren = existing.children.toMutableList()
                val existingChildIdx = updatedChildren.indexOfFirst {
                    it.name.equals(trimmedChildName, ignoreCase = true)
                }
                if (existingChildIdx >= 0) {
                    updatedChildren[existingChildIdx] = ChildEntry(
                        name = updatedChildren[existingChildIdx].name,
                        birthday = trimmedChildBirthday
                    )
                } else {
                    updatedChildren.add(ChildEntry(name = trimmedChildName, birthday = trimmedChildBirthday))
                }
                existing.copy(children = updatedChildren).also { current[targetIndex] = it }
            } else {
                PersonEntry(
                    name = trimmedParentName,
                    birthday = trimmedHint?.takeIf { it.toLocalDateOrNull() != null },
                    friendOf = trimmedFriendOf,
                    children = listOf(ChildEntry(name = trimmedChildName, birthday = trimmedChildBirthday))
                ).also { current.add(it) }
            }

            val updatedSettings = _settings.value.copy(hasPendingLocalChanges = true)
            _entries.value = current
            _settings.value = updatedSettings
            persistLocked(current, updatedSettings)
            resultEntry
        }

        triggerBackgroundSync()
        return updatedParent
    }

    override suspend fun deletePersonOrChild(
        name: String,
        birthdayHint: String?,
        parentNameHint: String?
    ): Boolean {
        ensureInitialized()
        val trimmedName = name.trim()
        val trimmedBirthday = birthdayHint?.trim()?.takeIf { it.isNotEmpty() }
        val trimmedParent = parentNameHint?.trim()?.takeIf { it.isNotEmpty() }

        val deleted = mutex.withLock {
            val current = _entries.value.toMutableList()
            var modified = false

            if (trimmedParent != null) {
                for (i in current.indices) {
                    val entry = current[i]
                    val parentMatches = entry.name.equals(trimmedParent, ignoreCase = true) ||
                        entry.partner?.name?.equals(trimmedParent, ignoreCase = true) == true
                    if (parentMatches) {
                        val remainingChildren = entry.children.filterNot { child ->
                            child.name.equals(trimmedName, ignoreCase = true) &&
                                (trimmedBirthday == null || child.birthday == trimmedBirthday || child.birthday.startsWith(trimmedBirthday))
                        }
                        if (remainingChildren.size != entry.children.size) {
                            current[i] = entry.copy(children = remainingChildren)
                            modified = true
                            break
                        }
                    }
                }
            }

            if (!modified) {
                val topLevelIndex = current.indexOfFirst { entry ->
                    entry.name.equals(trimmedName, ignoreCase = true) &&
                        (trimmedBirthday == null || entry.birthday == trimmedBirthday || entry.birthday?.startsWith(trimmedBirthday) == true)
                }
                if (topLevelIndex >= 0) {
                    current.removeAt(topLevelIndex)
                    modified = true
                }
            }

            if (!modified) {
                for (i in current.indices) {
                    val entry = current[i]
                    val partner = entry.partner
                    if (partner != null &&
                        partner.name.equals(trimmedName, ignoreCase = true) &&
                        (trimmedBirthday == null || partner.birthday == trimmedBirthday || partner.birthday?.startsWith(trimmedBirthday) == true)
                    ) {
                        current[i] = entry.copy(partner = null)
                        modified = true
                        break
                    }
                }
            }

            if (!modified) {
                for (i in current.indices) {
                    val entry = current[i]
                    val remainingChildren = entry.children.filterNot { child ->
                        child.name.equals(trimmedName, ignoreCase = true) &&
                            (trimmedBirthday == null || child.birthday == trimmedBirthday || child.birthday.startsWith(trimmedBirthday))
                    }
                    if (remainingChildren.size != entry.children.size) {
                        current[i] = entry.copy(children = remainingChildren)
                        modified = true
                        break
                    }
                }
            }

            if (modified) {
                val updatedSettings = _settings.value.copy(hasPendingLocalChanges = true)
                _entries.value = current
                _settings.value = updatedSettings
                persistLocked(current, updatedSettings)
            }
            modified
        }

        if (deleted) {
            triggerBackgroundSync()
        }
        return deleted
    }

    override suspend fun updateSettings(transform: (BdaiSettings) -> BdaiSettings) {
        ensureInitialized()
        mutex.withLock {
            val updated = transform(_settings.value)
            _settings.value = updated
            persistLocked(_entries.value, updated)
        }
    }

    override suspend fun syncWithNas(): SyncResult {
        ensureInitialized()
        val currentEntries = _entries.value
        val nasUrl = _settings.value.nasUrl.trim()
        if (nasUrl.isEmpty()) {
            return SyncResult.Error("NAS URL is empty")
        }

        return try {
            val payload = json.encodeToString(ListSerializer(PersonEntry.serializer()), currentEntries)
            val response = httpClient.post(nasUrl) {
                contentType(ContentType.Application.Json)
                setBody(payload)
            }
            if (!response.status.isSuccess()) {
                return SyncResult.Error("NAS sync failed with HTTP ${response.status.value}")
            }
            val nowMillis = clock.now().toEpochMilliseconds()
            mutex.withLock {
                val updatedSettings = _settings.value.copy(
                    lastSyncEpochMillis = nowMillis,
                    hasPendingLocalChanges = false
                )
                _settings.value = updatedSettings
                persistLocked(_entries.value, updatedSettings)
            }
            SyncResult.Success(currentEntries.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SyncResult.Error("${e.message ?: "Failed to sync"} ($nasUrl)")
        }
    }

    override suspend fun pullFromNas(): SyncResult {
        ensureInitialized()
        val nasUrl = _settings.value.nasUrl.trim()
        if (nasUrl.isEmpty()) {
            return SyncResult.Error("NAS URL is empty")
        }

        return try {
            val response = httpClient.get(nasUrl)
            if (!response.status.isSuccess()) {
                return SyncResult.Error("NAS pull failed with HTTP ${response.status.value} ($nasUrl)")
            }
            val bodyText = response.bodyAsText()
            val parsedEntries = parseNasPayload(bodyText)
            val nowMillis = clock.now().toEpochMilliseconds()
            mutex.withLock {
                val updatedSettings = _settings.value.copy(
                    lastSyncEpochMillis = nowMillis,
                    hasPendingLocalChanges = false
                )
                _entries.value = parsedEntries
                _settings.value = updatedSettings
                persistLocked(parsedEntries, updatedSettings)
            }
            SyncResult.Success(parsedEntries.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SyncResult.Error("${e.message ?: "Failed to pull"} ($nasUrl)")
        }
    }

    internal fun parseNasPayload(bodyText: String): List<PersonEntry> {
        val trimmed = bodyText.trim()
        if (trimmed.isEmpty()) return emptyList()
        if (!trimmed.startsWith("[") && !trimmed.startsWith("{")) {
            return BirthdayTxtFormat.parse(trimmed)
        }
        val element = json.parseToJsonElement(trimmed)
        val listSerializer = ListSerializer(PersonEntry.serializer())
        return when (element) {
            is JsonArray -> json.decodeFromJsonElement(listSerializer, element)
            is JsonObject -> {
                val array = (element["entries"] ?: element["birthdays"] ?: element["data"]) as? JsonArray
                    ?: throw IllegalArgumentException("Missing 'entries' array in NAS response")
                json.decodeFromJsonElement(listSerializer, array)
            }
            else -> throw IllegalArgumentException("Unexpected format from NAS")
        }
    }

    private fun triggerBackgroundSync() {
        scope.launch {
            runCatching { syncWithNas() }
        }
    }

    private suspend fun persistLocked(entries: List<PersonEntry>, settings: BdaiSettings) {
        val currentDao = dao ?: return
        val entriesJson = json.encodeToString(ListSerializer(PersonEntry.serializer()), entries)
        val settingsJson = json.encodeToString(BdaiSettings.serializer(), settings)
        currentDao.upsertStorage(
            BirthdayStorageEntity(
                id = BirthdayStorageEntity.SINGLETON_ID,
                entriesJson = entriesJson,
                settingsJson = settingsJson
            )
        )
    }

    private fun decodeEntries(entriesJson: String): List<PersonEntry> {
        return runCatching {
            json.decodeFromString(ListSerializer(PersonEntry.serializer()), entriesJson)
        }.getOrElse { seedEntries }
    }

    private fun decodeSettings(settingsJson: String): BdaiSettings {
        return runCatching {
            json.decodeFromString(BdaiSettings.serializer(), settingsJson)
        }.getOrElse { BdaiSettings() }
    }
}
