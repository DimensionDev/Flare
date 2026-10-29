package dev.dimension.flare.feature.plugin.authoring

import dev.dimension.flare.data.repository.AccountService
import dev.dimension.flare.data.repository.AuthoredPlugin
import dev.dimension.flare.data.repository.PluginAuthoring
import dev.dimension.flare.data.repository.PluginAuthoringPhase
import dev.dimension.flare.data.repository.PluginSource
import dev.dimension.flare.data.repository.authoredPluginPlatformId
import dev.dimension.flare.feature.plugin.PluginSubsystemV1
import dev.dimension.flare.feature.plugin.abi.PluginJsonV1
import dev.dimension.flare.feature.plugin.installer.FppArchive
import dev.dimension.flare.feature.plugin.installer.FppArchiveEntry
import dev.dimension.flare.feature.plugin.installer.FppLimits
import dev.dimension.flare.feature.plugin.installer.FppPaths
import dev.dimension.flare.feature.plugin.installer.PluginPackageValidator
import dev.dimension.flare.feature.plugin.installer.ValidatedPluginPackage
import dev.dimension.flare.feature.plugin.installer.encodePluginArchive
import dev.dimension.flare.feature.plugin.lifecycle.InstalledPluginV1
import dev.dimension.flare.feature.plugin.lifecycle.PluginRuntimeSwitcherV1
import dev.dimension.flare.feature.plugin.lifecycle.RunningPluginV1
import dev.dimension.flare.feature.plugin.lifecycle.derivedPluginState
import dev.dimension.flare.feature.plugin.manifest.fallbackText
import dev.dimension.flare.feature.plugin.runtime.PluginSessionBusyException
import dev.dimension.flare.model.PlatformRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString

internal class PluginAuthoringServiceV1(
    private val subsystem: PluginSubsystemV1,
    registry: () -> PlatformRegistry,
    accounts: () -> AccountService,
    scope: () -> CoroutineScope,
) : PluginAuthoring {
    private val scope by lazy(scope)
    private val files = subsystem.appFiles.fileSystem
    private val root = subsystem.appFiles.directory("authored-plugins")
    private val entries = MutableStateFlow(load())
    private val mutablePlugins = MutableStateFlow(entries.value.values.map(::snapshot))

    override val plugins: StateFlow<List<AuthoredPlugin>> =
        derivedPluginState(mutablePlugins, subsystem.running) { plugins, running ->
            plugins.map { it.copy(running = it.id in running.plugins) }
        }
    private val switcher by lazy { PluginRuntimeSwitcherV1(subsystem, registry(), accounts(), this.scope) }
    private val validator = PluginPackageValidator(files)

    override suspend fun create(requestKey: String): AuthoredPlugin =
        withContext(Dispatchers.Default) {
            require(Regex("[A-Za-z0-9_-]{16,128}").matches(requestKey)) {
                "Use a unique request key of 16 to 128 letters, digits, underscores or hyphens"
            }
            val id = "local.ai.p${requestKey.encodeToByteArray().toByteString().sha256().hex().take(32)}"
            subsystem.mutationMutex.withLock {
                if (id !in entries.value) {
                    check(id !in subsystem.stateStore.desired.value.plugins && id !in subsystem.running.value.plugins) {
                        "An installed plugin already uses this ID; use a different creation request key"
                    }
                    val entry = Entry(Record(id = id))
                    save(entry.record)
                    entries.update { it + (id to entry) }
                    publish(entry)
                }
                plugins.value.single { it.id == id }
            }
        }

    override suspend fun read(pluginId: String): PluginSource {
        val entry = entry(pluginId)
        return entry.mutex.withLock {
            check(entries.value[pluginId] === entry) { "Plugin no longer exists" }
            PluginSource(plugins.value.single { it.id == pluginId }, entry.record.sources)
        }
    }

    override suspend fun write(
        pluginId: String,
        expectedRevision: Long,
        files: Map<String, String>,
    ): AuthoredPlugin =
        withContext(Dispatchers.Default) {
            val entry = entry(pluginId)
            validateSources(files)
            entry.mutex.withLock {
                check(entries.value[pluginId] === entry) { "Plugin no longer exists" }
                check(entry.record.revision == expectedRevision) {
                    "Revision conflict: current revision is ${entry.record.revision}. Read the current source before retrying."
                }
                val updated = entry.record.copy(sources = files.toMap(), revision = entry.record.revision + 1)
                save(updated)
                entry.record = updated
                entry.candidate = null
                // Accepted sources belong to the plugin, even when the caller stops waiting.
                schedule(entry, revalidate = true)
                plugins.value.single { it.id == pluginId }
            }
        }

    override suspend fun documentation(path: String): String =
        if (path.isBlank()) {
            "Available SDK documents (start with README.md):\n" + BundledPluginSdk.paths.joinToString("\n")
        } else {
            requireNotNull(BundledPluginSdk.read(path)) { "Unknown SDK document" }
        }

    override suspend fun fetchDocumentation(url: String): String = fetchPluginDocumentation(url)

    override suspend fun cancelUpdate(pluginId: String) {
        val entry = entry(pluginId)
        entry.mutex.withLock {
            if (entries.value[pluginId] !== entry) return
            entry.application?.cancel()
            entry.candidate = null
            entry.progress = entry.progress.copy(phase = PluginAuthoringPhase.Idle)
            publish(entry)
        }
    }

    override suspend fun setEnabled(
        pluginId: String,
        enabled: Boolean,
    ): Unit =
        withContext(Dispatchers.Default) {
            val entry = entry(pluginId)
            entry.mutex.withLock {
                check(entries.value[pluginId] === entry) { "Plugin no longer exists" }
                if (entry.record.enabled == enabled) return@withLock
                subsystem.mutationMutex.withLock {
                    val previous = subsystem.running.value.plugins[pluginId]
                    check(previous?.developmentSource == null) { "Stop directory development before updating this plugin" }
                    check(
                        previous?.session?.hasWrites != true && !subsystem.oauth.hasPending(pluginId),
                    ) { "Wait for login or write operations to finish" }
                    val installed = subsystem.stateStore.desired.value.plugins[pluginId]
                    if (installed != null) {
                        val record =
                            installed.copy(
                                enabled = enabled,
                                runtimeGeneration = maxOf(previous?.generation ?: 0, installed.runtimeGeneration) + 1,
                            )
                        switcher.switch(previous, if (enabled) running(record) else null, record)
                    }
                }
                if (!enabled) {
                    entry.application?.cancel()
                    entry.candidate = null
                    entry.progress = entry.progress.copy(phase = PluginAuthoringPhase.Idle)
                }
                entry.record = entry.record.copy(enabled = enabled)
                publish(entry)
                save(entry.record)
                if (!enabled || entry.record.sources.isEmpty()) return@withLock
                schedule(entry, revalidate = entry.candidate == null)
            }
        }

    override suspend fun delete(pluginId: String): Unit =
        withContext(Dispatchers.Default) {
            val entry = entry(pluginId)
            entry.mutex.withLock {
                check(entries.value[pluginId] === entry) { "Plugin no longer exists" }
                subsystem.mutationMutex.withLock {
                    val previous = subsystem.running.value.plugins[pluginId]
                    check(previous?.developmentSource == null) { "Stop directory development before updating this plugin" }
                    check(
                        previous?.session?.hasWrites != true && !subsystem.oauth.hasPending(pluginId),
                    ) { "Wait for login or write operations to finish" }
                    val installed = subsystem.stateStore.desired.value.plugins[pluginId]
                    if (previous != null || installed != null) switcher.switch(previous, null, null, pluginId)
                    entry.application?.cancel()
                    files.delete(root / "$pluginId.json", mustExist = false)
                    entries.update { it - pluginId }
                    mutablePlugins.update { items -> items.filterNot { it.id == pluginId } }
                }
            }
        }

    private fun schedule(
        entry: Entry,
        revalidate: Boolean = false,
    ) {
        entry.application?.cancel()
        val record = entry.record
        if (revalidate) {
            entry.progress = Progress(phase = PluginAuthoringPhase.Validating)
            publish(entry)
        }
        entry.application =
            scope.launch {
                try {
                    if (revalidate) {
                        val candidate = prepare(record)
                        entry.mutex.withLock {
                            currentCoroutineContext().ensureActive()
                            if (entries.value[record.id] !== entry || entry.record.revision != record.revision) return@launch
                            accept(entry, candidate)
                        }
                    }
                    while (true) {
                        val complete =
                            entry.mutex.withLock {
                                currentCoroutineContext().ensureActive()
                                try {
                                    apply(entry)
                                } catch (_: PluginSessionBusyException) {
                                    entry.progress = entry.progress.copy(phase = PluginAuthoringPhase.WaitingForOperations)
                                    publish(entry)
                                    false
                                }
                            }
                        if (complete) break
                        delay(250)
                    }
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    entry.mutex.withLock {
                        currentCoroutineContext().ensureActive()
                        if (entries.value[record.id] !== entry || entry.record.revision != record.revision) return@launch
                        entry.progress = entry.progress.copy(phase = PluginAuthoringPhase.Failed, error = error.message?.take(512))
                        publish(entry)
                    }
                }
            }
    }

    private suspend fun apply(entry: Entry): Boolean {
        val candidate = entry.candidate ?: return true
        val id = entry.record.id
        if (entries.value[id] !== entry || candidate.revision != entry.record.revision) return true
        if (!entry.record.enabled) {
            entry.progress = entry.progress.copy(phase = PluginAuthoringPhase.Idle)
            publish(entry)
            return true
        }
        currentCoroutineContext().ensureActive()
        return subsystem.mutationMutex.withLock {
            val previous = subsystem.running.value.plugins[id]
            check(previous?.developmentSource == null) { "Stop directory development before updating this plugin" }
            val installed = subsystem.stateStore.desired.value.plugins[id]
            if (previous?.installed?.packageHash != candidate.packageHash) {
                if (previous?.session?.hasWrites == true || subsystem.oauth.hasPending(id)) {
                    entry.progress = entry.progress.copy(phase = PluginAuthoringPhase.WaitingForOperations)
                    publish(entry)
                    return@withLock false
                }
                val generation = maxOf(previous?.generation ?: 0, installed?.runtimeGeneration ?: 0) + 1
                val record = subsystem.installer.persistAuthored(candidate.validated, candidate.archive, true, generation)
                switcher.switch(previous, running(record), record)
            }
            entry.candidate = null
            entry.progress =
                entry.progress.copy(
                    phase = PluginAuthoringPhase.Applied,
                    error = null,
                )
            publish(entry)
            true
        }
    }

    private fun entry(id: String): Entry = requireNotNull(entries.value[id]) { "Plugin no longer exists" }

    private fun running(record: InstalledPluginV1): RunningPluginV1 =
        RunningPluginV1(
            record,
            subsystem.stateStore.paths
                .packagePath(record.packageHash)
                .toString(),
            subsystem.stateStore.paths
                .iconPath(record.packageHash)
                .toString(),
            generation = record.runtimeGeneration,
        )

    private fun publish(entry: Entry) {
        mutablePlugins.update { items -> (items.filterNot { it.id == entry.record.id } + snapshot(entry)).sortedBy { it.id } }
    }

    private fun snapshot(entry: Entry) =
        AuthoredPlugin(
            id = entry.record.id,
            name = entry.record.name,
            revision = entry.record.revision,
            enabled = entry.record.enabled,
            phase = entry.progress.phase,
            error = entry.progress.error,
        )

    private fun validateSources(files: Map<String, String>) {
        require(files.keys.containsAll(listOf(FppPaths.MANIFEST, FppPaths.ENTRY))) { "Submit manifest.json and plugin.js together" }
        require(
            files.size <= FppLimits.MAX_CATALOG_COUNT + 2 &&
                files.all { (path, text) ->
                    (path == FppPaths.MANIFEST || path == FppPaths.ENTRY || CATALOG.matches(path)) &&
                        text.encodeToByteArray().size <=
                        if (path == FppPaths.ENTRY) FppLimits.MAX_SCRIPT_BYTES else FppLimits.MAX_CATALOG_BYTES
                },
        ) { "Invalid plugin file or size" }
    }

    private suspend fun prepare(record: Record): Candidate {
        validateSources(record.sources)
        val bytes =
            record.sources.mapValues { it.value.encodeToByteArray() } +
                (FppPaths.ICON to requireNotNull(DEFAULT_PLUGIN_ICON.decodeBase64()).toByteArray())
        val archive =
            FppArchive(
                bytes.map { (path, value) ->
                    FppArchiveEntry(path, false, value.size.toLong(), value.size.toLong(), 0)
                },
                bytes,
            )
        val validated = validator.validate(archive)
        require(validated.manifest.id == record.id && validated.manifest.platform.id == authoredPluginPlatformId(record.id)) {
            "Keep the host-assigned plugin and platform IDs"
        }
        return Candidate(record.revision, validated, encodePluginArchive(bytes))
    }

    private fun accept(
        entry: Entry,
        candidate: Candidate,
    ) {
        val record = entry.record.copy(name = candidate.validated.manifest.name.fallbackText)
        save(record)
        entry.record = record
        entry.candidate = candidate
    }

    private fun save(record: Record) {
        val encoded = PluginJsonV1.encodeToString(record)
        require(encoded.encodeToByteArray().size <= MAX_RECORD_BYTES) { "Plugin source exceeds its size limit" }
        files.createDirectories(root)
        val temporary = root / "${record.id}.next"
        try {
            files.write(temporary) { writeUtf8(encoded) }
            files.atomicMove(temporary, root / "${record.id}.json")
        } finally {
            files.delete(temporary, mustExist = false)
        }
    }

    private fun load(): Map<String, Entry> =
        files
            .listOrNull(root)
            .orEmpty()
            .mapNotNull { path ->
                val id = path.name.removeSuffix(".json")
                if (!path.name.endsWith(".json") || !PLUGIN_ID.matches(id)) return@mapNotNull null
                val installed = subsystem.stateStore.desired.value.plugins[id]
                val loaded =
                    runCatching {
                        require((files.metadata(path).size ?: 0) <= MAX_RECORD_BYTES) { "Plugin source exceeds its size limit" }
                        PluginJsonV1.decodeFromString<Record>(files.read(path) { readUtf8() }).also {
                            require(it.id == id && it.revision >= 0) { "Invalid authored plugin identity" }
                        }
                    }
                val record =
                    loaded
                        .getOrElse {
                            Record(
                                id = id,
                                enabled = false,
                            )
                        }.let { it.copy(enabled = installed?.enabled ?: it.enabled) }
                val entry = Entry(record)
                entry.progress =
                    entry.progress.copy(
                        error = loaded.exceptionOrNull()?.message?.take(512),
                        // Saved source may be newer than the last working installation.
                        phase = if (loaded.isFailure) PluginAuthoringPhase.Failed else PluginAuthoringPhase.Idle,
                    )
                id to entry
            }.toMap()

    private class Entry(
        var record: Record,
    ) {
        val mutex = Mutex()
        var progress = Progress()
        var application: Job? = null
        var candidate: Candidate? = null
    }

    private data class Progress(
        val phase: PluginAuthoringPhase = PluginAuthoringPhase.Idle,
        val error: String? = null,
    )

    @Serializable
    private data class Record(
        val id: String,
        val name: String = "",
        val sources: Map<String, String> = emptyMap(),
        val revision: Long = 0,
        val enabled: Boolean = true,
    )

    private data class Candidate(
        val revision: Long,
        val validated: ValidatedPluginPackage,
        val archive: ByteArray,
    ) {
        val packageHash = archive.toByteString().sha256().hex()
    }

    private companion object {
        private val PLUGIN_ID = Regex("local\\.ai\\.p[a-f0-9]{32}")
        private val CATALOG = Regex("locales/[A-Za-z0-9_-]+\\.json")
        private const val MAX_RECORD_BYTES = 32L * 1024 * 1024
    }
}
