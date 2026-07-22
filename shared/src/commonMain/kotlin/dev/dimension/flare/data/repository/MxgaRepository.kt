package dev.dimension.flare.data.repository

import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.common.decodeProtobuf
import dev.dimension.flare.common.encodeProtobuf
import dev.dimension.flare.data.io.FileStorage
import dev.dimension.flare.data.network.httpClientEngine
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.contentPostOrNull
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.utils.io.cancel
import io.ktor.utils.io.core.readText
import io.ktor.utils.io.core.remaining
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.koin.core.annotation.Single
import kotlin.time.Clock

@Single(createdAtStart = true)
internal class MxgaRepository(
    private val fileStorage: FileStorage,
    private val settingsRepository: SettingsRepository,
    private val scope: CoroutineScope,
) {
    private val httpClient =
        HttpClient(httpClientEngine) {
            followRedirects = false
            install(HttpTimeout) {
                connectTimeoutMillis = NETWORK_TIMEOUT_MILLIS
                requestTimeoutMillis = NETWORK_TIMEOUT_MILLIS
                socketTimeoutMillis = NETWORK_TIMEOUT_MILLIS
            }
        }
    private val mutex = Mutex()
    private val cachePath = fileStorage.dataStoreFile(MXGA_CACHE_FILE)
    private var loaded = false
    private var lastRefreshFailed = false
    private var manualRefreshJob: Job? = null
    private val mutableIsRefreshing = MutableStateFlow(false)
    private val mutableSnapshot = MutableStateFlow(MxgaSnapshot())

    internal val isRefreshing: StateFlow<Boolean> = mutableIsRefreshing.asStateFlow()
    internal val snapshot: StateFlow<MxgaSnapshot> = mutableSnapshot.asStateFlow()

    init {
        scope.launch {
            mutex.withLock { loadStoredSnapshotLocked() }
            settingsRepository.appSettings
                .map { it.mxgaEnabled }
                .distinctUntilChanged()
                .collectLatest { enabled ->
                    if (enabled) {
                        runAutoRefreshLoop()
                    }
                }
        }
    }

    fun refresh() {
        if (manualRefreshJob?.isActive == true) return
        manualRefreshJob =
            scope.launch {
                mutex.withLock {
                    loadStoredSnapshotLocked()
                    refreshLocked()
                }
            }
    }

    private suspend fun runAutoRefreshLoop() {
        while (currentCoroutineContext().isActive) {
            val nextDelay =
                mutex.withLock {
                    loadStoredSnapshotLocked()
                    val now = Clock.System.now().toEpochMilliseconds()
                    if (lastRefreshFailed || mutableSnapshot.value.isDue(now)) {
                        refreshLocked()
                    }
                    if (lastRefreshFailed) {
                        RETRY_INTERVAL_MILLIS
                    } else {
                        (mutableSnapshot.value.checkedAt + UPDATE_INTERVAL_MILLIS - Clock.System.now().toEpochMilliseconds())
                            .coerceAtLeast(MINIMUM_LOOP_DELAY_MILLIS)
                    }
                }
            delay(nextDelay)
        }
    }

    private suspend fun loadStoredSnapshotLocked() {
        if (loaded) return
        try {
            val stored = cachePath.takeIf(fileStorage::exists)?.let(fileStorage::read)
            loaded = true
            if (stored == null) return
            mutableSnapshot.value = stored.decodeProtobuf()
        } catch (cause: Throwable) {
            if (cause is CancellationException) throw cause
            DebugRepository.error(cause)
        }
    }

    private suspend fun refreshLocked() {
        mutableIsRefreshing.value = true
        lastRefreshFailed = false
        try {
            val current = mutableSnapshot.value
            val whitelist =
                MxgaDocumentParser.parseWhitelist(
                    fetchText(WHITELIST_PATH, MAX_WHITELIST_BYTES),
                )
            require(current.whitelistHandles.isEmpty() || whitelist.handles.isNotEmpty()) {
                "MXGA whitelist unexpectedly became empty"
            }
            val meta = MxgaDocumentParser.parseMeta(fetchText(META_PATH, MAX_META_BYTES))
            val checkedAt = Clock.System.now().toEpochMilliseconds()
            val updated =
                if (current.version == meta.version) {
                    current.copy(checkedAt = checkedAt)
                } else {
                    MxgaDocumentParser
                        .parseLite(
                            value = fetchText(meta.litePath, MAX_LITE_BYTES),
                            expectedVersion = meta.version,
                            expectedCount = meta.count,
                        ).copy(checkedAt = checkedAt)
                }
            persistAndPublish(updated.withWhitelist(whitelist))
        } catch (cause: Throwable) {
            if (cause is CancellationException) throw cause
            lastRefreshFailed = true
            DebugRepository.error(cause)
        } finally {
            mutableIsRefreshing.value = false
        }
    }

    private suspend fun fetchText(
        path: String,
        maxBytes: Long,
    ): String {
        require(path.startsWith("/") && !path.startsWith("//")) { "Invalid MXGA path" }
        val response = httpClient.get(BASE_URL + path)
        val channel = response.bodyAsChannel()
        if (!response.status.isSuccess()) {
            channel.cancel()
            error("MXGA request failed: ${response.status.value}")
        }
        val contentLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
        if (contentLength != null && contentLength > maxBytes) {
            channel.cancel()
            error("MXGA response is too large")
        }
        val body = channel.readRemaining(maxBytes + 1)
        if (body.remaining > maxBytes) {
            channel.cancel()
            error("MXGA response is too large")
        }
        return body.readText()
    }

    private suspend fun persistAndPublish(snapshot: MxgaSnapshot) {
        fileStorage.write(cachePath, snapshot.encodeProtobuf())
        mutableSnapshot.value = snapshot
    }

    private companion object {
        const val BASE_URL = "https://x.zuoluo.tv"
        const val META_PATH = "/v1/list/meta"
        const val WHITELIST_PATH = "/v1/whitelist"
        const val MXGA_CACHE_FILE = "mxga_snapshot.pb"
        const val NETWORK_TIMEOUT_MILLIS = 30_000L
        const val RETRY_INTERVAL_MILLIS = 60L * 60L * 1000L
        const val MINIMUM_LOOP_DELAY_MILLIS = 1_000L
        const val MAX_META_BYTES = 64L * 1024L
        const val MAX_WHITELIST_BYTES = 2L * 1024L * 1024L
        const val MAX_LITE_BYTES = 25L * 1024L * 1024L
    }
}

@Serializable
internal data class MxgaSnapshot(
    val version: String = "",
    val checkedAt: Long = 0L,
    val blockedIds: LongArray = longArrayOf(),
    val blockedOverflowIds: List<String> = emptyList(),
    val blockedHandles: List<String> = emptyList(),
    val whitelistIds: LongArray = longArrayOf(),
    val whitelistOverflowIds: List<String> = emptyList(),
    val whitelistHandles: List<String> = emptyList(),
    val rules: List<MxgaRule> = emptyList(),
) {
    fun matches(signals: MxgaSignals): Boolean {
        if (version.isEmpty() && rules.isEmpty()) return false
        val handle =
            signals.handle
                .trim()
                .removePrefix("@")
                .substringBefore("@")
                .lowercase()
        if (containsId(whitelistIds, whitelistOverflowIds, signals.userId) ||
            whitelistHandles.binarySearch(handle) >= 0
        ) {
            return false
        }
        if (containsId(blockedIds, blockedOverflowIds, signals.userId)) return true
        if (signals.userId.isBlank() && blockedHandles.binarySearch(handle) >= 0) return true
        if (rules.isEmpty()) return false

        val loweredName = signals.displayName.lowercase()
        val loweredBio = signals.bio.lowercase()
        val loweredText = signals.text.lowercase()
        return rules.any { rule ->
            when (rule.field) {
                'h' -> {
                    handle.contains(rule.pattern)
                }

                'd' -> {
                    loweredName.contains(rule.pattern)
                }

                'b' -> {
                    loweredBio.contains(rule.pattern)
                }

                't' -> {
                    loweredText.contains(rule.pattern)
                }

                'a' -> {
                    handle.contains(rule.pattern) ||
                        loweredName.contains(rule.pattern) ||
                        loweredBio.contains(rule.pattern) ||
                        loweredText.contains(rule.pattern)
                }

                else -> {
                    false
                }
            }
        }
    }

    fun withWhitelist(whitelist: MxgaWhitelist): MxgaSnapshot =
        copy(
            whitelistIds = whitelist.ids,
            whitelistOverflowIds = whitelist.overflowIds,
            whitelistHandles = whitelist.handles,
        )

    fun isDue(now: Long): Boolean = checkedAt <= 0L || now - checkedAt >= UPDATE_INTERVAL_MILLIS
}

@Serializable
internal data class MxgaRule(
    val pattern: String,
    val field: Char,
)

internal data class MxgaSignals(
    val userId: String,
    val handle: String,
    val displayName: String,
    val bio: String,
    val text: String,
)

internal fun UiTimelineV2.isMxgaMatch(snapshot: MxgaSnapshot): Boolean {
    val post = contentPostOrNull() ?: return false
    if (post.platformType != PlatformType.xQt) return false
    val user = post.user ?: return false
    return snapshot.matches(
        MxgaSignals(
            userId = user.key.id,
            handle = user.handle.raw,
            displayName = user.name.innerText,
            bio = user.description?.innerText.orEmpty(),
            text = post.content.original.innerText,
        ),
    )
}

internal data class MxgaMeta(
    val version: String,
    val count: Int,
    val litePath: String,
)

internal data class MxgaWhitelist(
    val ids: LongArray,
    val overflowIds: List<String>,
    val handles: List<String>,
)

@Serializable
private data class MxgaMetaDocument(
    val version: String,
    val count: Int,
    val artifacts: MxgaArtifactsDocument,
)

@Serializable
private data class MxgaArtifactsDocument(
    val lite: String,
)

@Serializable
private data class MxgaWhitelistDocument(
    val count: Int,
    val list: List<MxgaWhitelistRow>,
)

@Serializable
private data class MxgaWhitelistRow(
    @SerialName("x_user_id") val userId: String? = null,
    val handle: String,
)

@Serializable
private data class MxgaLiteDocument(
    val schema: Int,
    val version: String,
    val count: Int,
    val rules: List<List<String>>,
    val entries: List<List<String>>,
)

internal object MxgaDocumentParser {
    fun parseMeta(value: String): MxgaMeta {
        val root = value.decodeJson<MxgaMetaDocument>()
        val version = root.version
        require(VERSION_REGEX.matches(version)) { "Invalid MXGA version" }
        val count = root.count
        require(count in MIN_ENTRIES..MAX_ENTRIES) { "Invalid MXGA entry count" }
        val litePath = root.artifacts.lite
        require(LITE_PATH_REGEX.matches(litePath)) { "Invalid MXGA lite path" }
        return MxgaMeta(version = version, count = count, litePath = litePath)
    }

    fun parseWhitelist(value: String): MxgaWhitelist {
        val root = value.decodeJson<MxgaWhitelistDocument>()
        val rows = root.list
        val count = root.count
        require(count == rows.size && count <= MAX_WHITELIST_ENTRIES) { "Invalid MXGA whitelist count" }
        val ids = ArrayList<Long>(count)
        val overflowIds = ArrayList<String>()
        val handles = ArrayList<String>(count)
        rows.forEach { row ->
            val id = row.userId
            val handle = row.handle
            require(id == null || ID_REGEX.matches(id)) { "Invalid MXGA whitelist id" }
            require(HANDLE_REGEX.matches(handle)) { "Invalid MXGA whitelist handle" }
            if (id != null) {
                id.toLongOrNull()?.let(ids::add) ?: overflowIds.add(id)
            }
            handles.add(handle.lowercase())
        }
        return MxgaWhitelist(
            ids = ids.distinct().sorted().toLongArray(),
            overflowIds = overflowIds.distinct().sorted(),
            handles = handles.distinct().sorted(),
        )
    }

    fun parseLite(
        value: String,
        expectedVersion: String,
        expectedCount: Int,
    ): MxgaSnapshot {
        val root = value.decodeJson<MxgaLiteDocument>()
        require(root.schema == LITE_SCHEMA) { "Unsupported MXGA lite schema" }
        val version = root.version
        require(version == expectedVersion && VERSION_REGEX.matches(version)) { "MXGA version mismatch" }
        val rows = root.entries
        val count = root.count
        require(count == expectedCount && count == rows.size && count in MIN_ENTRIES..MAX_ENTRIES) {
            "MXGA entry count mismatch"
        }
        val numericEntries = ArrayList<Long>(count)
        val overflowEntries = ArrayList<String>()
        val handleEntries = ArrayList<String>()
        rows.forEach { row ->
            require(row.size == 3) { "Invalid MXGA entry row" }
            val id = row[0]
            val handle = row[1]
            require(id.isEmpty() || ID_REGEX.matches(id)) { "Invalid MXGA entry id" }
            require(HANDLE_REGEX.matches(handle)) { "Invalid MXGA entry handle" }
            if (id.isEmpty()) {
                handleEntries.add(handle.lowercase())
            } else {
                id
                    .toLongOrNull()
                    ?.let(numericEntries::add)
                    ?: overflowEntries.add(id)
            }
        }

        val rules = root.rules
        require(rules.size <= MAX_RULES) { "Too many MXGA rules" }
        val parsedRules =
            rules.map { row ->
                require(row.size == 3) { "Invalid MXGA rule row" }
                val pattern = row[0]
                val field = row[1].singleOrNull()
                require(pattern.isNotBlank() && pattern.length <= MAX_PATTERN_LENGTH) { "Invalid MXGA rule pattern" }
                require(field != null && field in "hdbta") { "Invalid MXGA rule field" }
                MxgaRule(
                    pattern = pattern.lowercase(),
                    field = field,
                )
            }

        numericEntries.sort()
        overflowEntries.sort()
        handleEntries.sort()
        return MxgaSnapshot(
            version = version,
            blockedIds = numericEntries.toLongArray(),
            blockedOverflowIds = overflowEntries,
            blockedHandles = handleEntries,
            rules = parsedRules,
        )
    }
}

private fun containsId(
    numericIds: LongArray,
    overflowIds: List<String>,
    value: String,
): Boolean {
    if (value.isBlank()) return false
    val numeric = value.toLongOrNull()
    return if (numeric != null) {
        numericIds.containsSorted(numeric)
    } else {
        overflowIds.binarySearch(value) >= 0
    }
}

private fun LongArray.containsSorted(value: Long): Boolean {
    var low = 0
    var high = lastIndex
    while (low <= high) {
        val middle = (low + high).ushr(1)
        when {
            this[middle] < value -> low = middle + 1
            this[middle] > value -> high = middle - 1
            else -> return true
        }
    }
    return false
}

private const val LITE_SCHEMA = 2
private const val MIN_ENTRIES = 1_000
private const val MAX_ENTRIES = 250_000
private const val MAX_WHITELIST_ENTRIES = 50_000
private const val MAX_RULES = 10_000
private const val MAX_PATTERN_LENGTH = 200
private const val MAX_VERSION_LENGTH = 128
private const val UPDATE_INTERVAL_MILLIS = 24L * 60L * 60L * 1000L
private val VERSION_REGEX = Regex("^[A-Za-z0-9._-]{1,$MAX_VERSION_LENGTH}$")
private val LITE_PATH_REGEX = Regex("^/v1/artifacts/[A-Za-z0-9._-]+$")
private val ID_REGEX = Regex("^\\d{1,32}$")
private val HANDLE_REGEX = Regex("^[A-Za-z0-9_]{1,15}$")
