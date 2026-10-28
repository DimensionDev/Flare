package dev.dimension.flare.feature.plugin.authoring

import dev.dimension.flare.data.model.PluginPermission
import dev.dimension.flare.data.repository.AccountService
import dev.dimension.flare.data.repository.AuthoredPlugin
import dev.dimension.flare.data.repository.PluginAuthoring
import dev.dimension.flare.data.repository.PluginAuthoringPhase
import dev.dimension.flare.feature.plugin.PluginSubsystemV1
import dev.dimension.flare.feature.plugin.abi.PluginJsonV1
import dev.dimension.flare.feature.plugin.host.platformUuid
import dev.dimension.flare.feature.plugin.installer.FppArchive
import dev.dimension.flare.feature.plugin.installer.FppArchiveEntry
import dev.dimension.flare.feature.plugin.installer.FppLimits
import dev.dimension.flare.feature.plugin.installer.FppPaths
import dev.dimension.flare.feature.plugin.installer.PluginPackageValidator
import dev.dimension.flare.feature.plugin.installer.ValidatedPluginPackage
import dev.dimension.flare.feature.plugin.installer.encodePluginArchive
import dev.dimension.flare.feature.plugin.installer.sensitivePermissions
import dev.dimension.flare.feature.plugin.lifecycle.InstalledPluginV1
import dev.dimension.flare.feature.plugin.lifecycle.PluginRuntimeSwitcherV1
import dev.dimension.flare.feature.plugin.lifecycle.RunningPluginV1
import dev.dimension.flare.feature.plugin.manifest.fallbackText
import dev.dimension.flare.feature.plugin.runtime.PluginSessionBusyException
import dev.dimension.flare.model.PlatformRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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

    // Derive runtime flags on reads and collection without an application-scope observer.
    @OptIn(ExperimentalForInheritanceCoroutinesApi::class)
    override val plugins: StateFlow<List<AuthoredPlugin>> =
        object : StateFlow<List<AuthoredPlugin>> {
            override val value: List<AuthoredPlugin>
                get() {
                    val running = subsystem.running.value.plugins
                    return mutablePlugins.value.map { it.copy(running = it.id in running) }
                }
            override val replayCache get() = listOf(value)

            override suspend fun collect(collector: FlowCollector<List<AuthoredPlugin>>): Nothing {
                combine(mutablePlugins, subsystem.running) { _, _ -> value }.distinctUntilChanged().collect(collector)
                awaitCancellation()
            }
        }
    private val switcher by lazy { PluginRuntimeSwitcherV1(subsystem, registry(), accounts(), this.scope) }
    private val validator = PluginPackageValidator(files)

    override suspend fun create(): AuthoredPlugin =
        withContext(Dispatchers.Default) {
            val record = Record(id = "local.ai.p${platformUuid().replace("-", "")}")
            val entry = Entry(record)
            save(record)
            entries.update { it + (record.id to entry) }
            publish(entry)
            plugins.value.single { it.id == record.id }
        }

    override suspend fun begin(pluginId: String): String {
        val entry = entry(pluginId)
        val caller = currentCoroutineContext()[Job]
        return entry.mutex.withLock {
            check(entries.value[pluginId] === entry) { "Plugin no longer exists" }
            check(entry.runId == null) { "This plugin already has a generation task" }
            entry.application?.cancel()
            entry.candidate = null
            entry.approvedRevision = null
            val run = platformUuid()
            entry.runId = run
            entry.caller = caller
            entry.progress =
                entry.progress.copy(
                    phase = PluginAuthoringPhase.Generating,
                    error = null,
                    permissions = emptyList(),
                    credentialsChanged = false,
                )
            publish(entry)
            run
        }
    }

    override suspend fun read(
        pluginId: String,
        runId: String,
    ): Map<String, String> {
        val entry = entry(pluginId)
        return entry.mutex.withLock {
            checkRun(entry, runId)
            entry.record.sources
        }
    }

    override suspend fun write(
        pluginId: String,
        runId: String,
        files: Map<String, String>,
    ): String =
        withContext(Dispatchers.Default) {
            val entry = entry(pluginId)
            validateSources(files)
            val record =
                entry.mutex.withLock {
                    checkRun(entry, runId)
                    val updated = entry.record.copy(sources = files.toMap(), revision = entry.record.revision + 1)
                    save(updated)
                    entry.record = updated
                    entry.candidate = null
                    entry.approvedRevision = null
                    entry.progress =
                        entry.progress.copy(
                            phase = PluginAuthoringPhase.Validating,
                            error = null,
                            permissions = emptyList(),
                            credentialsChanged = false,
                        )
                    publish(entry)
                    updated
                }
            try {
                val candidate = prepare(record)
                entry.mutex.withLock {
                    checkRun(entry, runId)
                    check(entry.record.revision == record.revision) { "A newer plugin revision already exists" }
                    accept(entry, candidate)
                    entry.progress = entry.progress.copy(phase = PluginAuthoringPhase.Generating)
                    publish(entry)
                }
                "Validation passed for revision ${record.revision}. It will be applied when this generation finishes, " +
                    "subject to host permission and in-flight operation checks. Live website behavior has not been " +
                    "tested."
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                entry.mutex.withLock {
                    if (entry.runId == runId && entry.record.revision == record.revision) {
                        entry.progress = entry.progress.copy(phase = PluginAuthoringPhase.Generating, error = error.message?.take(512))
                        publish(entry)
                    }
                }
                "Validation failed: ${error.message?.take(512)}"
            }
        }

    override suspend fun finish(
        pluginId: String,
        runId: String,
        apply: Boolean,
    ) {
        val entry = entries.value[pluginId] ?: return
        entry.mutex.withLock {
            if (entry.runId != runId) return
            entry.runId = null
            entry.caller = null
            if (apply && entry.candidate != null) {
                schedule(entry)
            } else {
                entry.candidate = null
                val phase = if (entry.progress.error == null) PluginAuthoringPhase.Idle else PluginAuthoringPhase.Failed
                entry.progress = entry.progress.copy(phase = phase)
                publish(entry)
            }
        }
    }

    override suspend fun documentation(path: String): String =
        if (path.isBlank()) {
            "Available SDK documents:\n" + BundledPluginSdk.documents.keys.joinToString("\n")
        } else {
            requireNotNull(BundledPluginSdk.documents[path]) { "Unknown SDK document" }
        }

    override suspend fun fetchDocumentation(url: String): String = fetchPluginDocumentation(url)

    override suspend fun approve(
        pluginId: String,
        revision: Long,
    ) {
        val entry = entry(pluginId)
        entry.mutex.withLock {
            check(entries.value[pluginId] === entry) { "Plugin no longer exists" }
            check(
                entry.progress.phase == PluginAuthoringPhase.AwaitingApproval && entry.candidate?.revision == revision &&
                    entry.record.revision == revision,
            ) { "The plugin changed; review its current version" }
            entry.approvedRevision = revision
            schedule(entry)
        }
    }

    override suspend fun stop(pluginId: String) {
        val entry = entry(pluginId)
        entry.mutex.withLock {
            if (entries.value[pluginId] !== entry) return
            entry.runId = null
            entry.caller?.cancel()
            entry.caller = null
            entry.application?.cancel()
            entry.candidate = null
            entry.approvedRevision = null
            entry.progress = entry.progress.copy(phase = PluginAuthoringPhase.Idle, permissions = emptyList(), credentialsChanged = false)
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
                    entry.approvedRevision = null
                    entry.progress =
                        entry.progress.copy(
                            phase =
                                if (entry.runId ==
                                    null
                                ) {
                                    PluginAuthoringPhase.Idle
                                } else {
                                    entry.progress.phase
                                },
                            permissions = emptyList(),
                            credentialsChanged = false,
                        )
                }
                entry.record = entry.record.copy(enabled = enabled)
                publish(entry)
                save(entry.record)
                if (!enabled || entry.runId != null || entry.record.sources.isEmpty()) return@withLock
                schedule(entry, revalidate = entry.candidate == null)
            }
        }

    override suspend fun delete(pluginId: String): Unit =
        withContext(Dispatchers.Default) {
            stop(pluginId)
            val entry = entry(pluginId)
            entry.mutex.withLock {
                subsystem.mutationMutex.withLock {
                    val previous = subsystem.running.value.plugins[pluginId]
                    check(previous?.developmentSource == null) { "Stop directory development before updating this plugin" }
                    check(
                        previous?.session?.hasWrites != true && !subsystem.oauth.hasPending(pluginId),
                    ) { "Wait for login or write operations to finish" }
                    val installed = subsystem.stateStore.desired.value.plugins[pluginId]
                    if (previous != null || installed != null) switcher.switch(previous, null, null, pluginId)
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
                        if (entries.value[record.id] !== entry) return@launch
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
            val baseline = previous?.installed ?: installed
            val additional = candidate.validated.manifest.sensitivePermissions() - baseline?.manifest?.sensitivePermissions().orEmpty()
            val schemaChanged =
                baseline != null &&
                    baseline.manifest.platform.credentialSchemaVersion != candidate.validated.manifest.platform.credentialSchemaVersion
            if ((additional.isNotEmpty() || schemaChanged) && entry.approvedRevision != candidate.revision) {
                entry.progress =
                    entry.progress.copy(
                        phase = PluginAuthoringPhase.AwaitingApproval,
                        permissions = additional.toList(),
                        credentialsChanged = schemaChanged,
                    )
                publish(entry)
                return@withLock true
            }
            if (previous?.installed?.packageHash != candidate.packageHash) {
                if (previous?.session?.hasWrites == true || subsystem.oauth.hasPending(id)) {
                    entry.progress =
                        entry.progress.copy(
                            phase = PluginAuthoringPhase.WaitingForOperations,
                            permissions = emptyList(),
                            credentialsChanged = false,
                        )
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
                    permissions = emptyList(),
                    credentialsChanged = false,
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

    private fun checkRun(
        entry: Entry,
        runId: String,
    ) {
        check(entries.value[entry.record.id] === entry && entry.runId == runId) { "Plugin generation was stopped or replaced" }
    }

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
            permissions = entry.progress.permissions,
            credentialsChanged = entry.progress.credentialsChanged,
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
                (FppPaths.ICON to requireNotNull(BundledPluginSdk.icon.decodeBase64()).toByteArray())
        val archive =
            FppArchive(
                bytes.map { (path, value) ->
                    FppArchiveEntry(path, false, value.size.toLong(), value.size.toLong(), 0)
                },
                bytes,
            )
        val validated = validator.validate(archive)
        require(validated.manifest.id == record.id && validated.manifest.platform.id == platformId(record.id)) {
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
        var runId: String? = null
        var caller: Job? = null
        var application: Job? = null
        var candidate: Candidate? = null
        var approvedRevision: Long? = null
    }

    private data class Progress(
        val phase: PluginAuthoringPhase = PluginAuthoringPhase.Idle,
        val error: String? = null,
        val permissions: List<PluginPermission> = emptyList(),
        val credentialsChanged: Boolean = false,
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

    companion object {
        fun platformId(id: String): String = id.removePrefix("local.").replace('.', '_')

        private val PLUGIN_ID = Regex("local\\.ai\\.p[a-f0-9]{32}")
        private val CATALOG = Regex("locales/[A-Za-z0-9_-]+\\.json")
        private const val MAX_RECORD_BYTES = 32L * 1024 * 1024
    }
}
