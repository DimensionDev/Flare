package dev.dimension.flare.feature.plugin.runtime

import dev.dimension.flare.feature.plugin.abi.PluginJsonV1
import dev.dimension.flare.feature.plugin.host.PluginCallTimeoutV1
import dev.dimension.flare.feature.plugin.host.PluginHostGateway
import dev.dimension.flare.feature.plugin.host.PluginHttpTransport
import dev.dimension.flare.feature.plugin.host.PluginInvocationContextV1
import dev.dimension.flare.feature.plugin.host.PluginInvocationScopeV1
import dev.dimension.flare.feature.plugin.host.defaultPluginRuntimeHeapBudgetBytes
import dev.dimension.flare.feature.plugin.lifecycle.RunningPluginV1
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import okio.FileSystem
import kotlin.native.HiddenFromObjC

public data class PluginRuntimeKeyV1 private constructor(
    val pluginId: String,
    val packageHash: String,
    val scope: PluginInvocationScopeV1,
    val identity: String,
) {
    public companion object {
        public fun detector(
            pluginId: String,
            packageHash: String,
            attemptId: String,
        ): PluginRuntimeKeyV1 = create(pluginId, packageHash, PluginInvocationScopeV1.Detector, attemptId)

        public fun login(
            pluginId: String,
            packageHash: String,
            flowId: String,
        ): PluginRuntimeKeyV1 = create(pluginId, packageHash, PluginInvocationScopeV1.Login, flowId)

        public fun account(
            pluginId: String,
            packageHash: String,
            accountKey: String,
        ): PluginRuntimeKeyV1 = create(pluginId, packageHash, PluginInvocationScopeV1.Account, accountKey)

        public fun guest(
            pluginId: String,
            packageHash: String,
            origin: String,
        ): PluginRuntimeKeyV1 = create(pluginId, packageHash, PluginInvocationScopeV1.Guest, origin)

        private fun create(
            pluginId: String,
            packageHash: String,
            scope: PluginInvocationScopeV1,
            identity: String,
        ): PluginRuntimeKeyV1 {
            require(pluginId.isNotBlank() && HASH.matches(packageHash) && identity.isNotBlank() && identity.length <= 1_024) {
                "Invalid Runtime key"
            }
            return PluginRuntimeKeyV1(pluginId, packageHash, scope, identity)
        }
    }
}

public data class PluginRuntimeIssueV1(
    val pluginId: String,
    val code: String,
    val fatalFailures: Int,
    val paused: Boolean,
)

@HiddenFromObjC
public class PluginRuntimePool(
    fileSystem: FileSystem,
    transport: PluginHttpTransport,
    private val softHeapBudgetBytes: Long = defaultPluginRuntimeHeapBudgetBytes(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val nowMillis: () -> Long = monotonicMillis(),
) {
    private val mutex = Mutex()
    private val loader = PluginPackageScriptLoader(fileSystem, dispatcher)
    private val gateway = PluginHostGateway(transport)
    private val holders = mutableMapOf<PluginRuntimeKeyV1, Holder>()
    private val failures = mutableMapOf<String, MutableList<Long>>()
    private val paused = mutableSetOf<String>()
    private var closed = false
    private val mutableIssues = MutableStateFlow<Map<String, PluginRuntimeIssueV1>>(emptyMap())

    public val issues: StateFlow<Map<String, PluginRuntimeIssueV1>> = mutableIssues.asStateFlow()

    init {
        require(softHeapBudgetBytes > 0) { "Invalid Runtime heap budget" }
    }

    public suspend fun <Request, Response> invoke(
        plugin: RunningPluginV1,
        key: PluginRuntimeKeyV1,
        context: PluginInvocationContextV1,
        method: String,
        request: Request,
        requestSerializer: KSerializer<Request>,
        responseSerializer: KSerializer<Response>,
        timeout: PluginCallTimeoutV1 = PluginCallTimeoutV1.Normal,
        validate: (Response) -> Unit = {},
    ): Response {
        var decoded: Any? = null
        var decodedSet = false
        val value = PluginJsonV1.encodeToJsonElement(requestSerializer, request)
        invokeJson(plugin, key, context, method, value, timeout) { response ->
            val result = PluginJsonV1.decodeFromJsonElement(responseSerializer, response)
            validate(result)
            decoded = result
            decodedSet = true
        }
        check(decodedSet) { "Plugin response was not decoded" }
        @Suppress("UNCHECKED_CAST")
        return decoded as Response
    }

    public suspend fun invokeJson(
        plugin: RunningPluginV1,
        key: PluginRuntimeKeyV1,
        context: PluginInvocationContextV1,
        method: String,
        request: JsonElement,
        timeout: PluginCallTimeoutV1 = PluginCallTimeoutV1.Normal,
        validate: (JsonElement) -> Unit = {},
    ): JsonElement {
        validateInvocation(plugin, key, context)
        if (key.scope == PluginInvocationScopeV1.Detector) {
            return invokeTemporary(plugin, context, method, request, timeout, validate)
        }
        val holder =
            mutex.withLock {
                check(!closed) { "Plugin Runtime pool is closed" }
                if (plugin.installed.pluginId in paused) throw PluginRuntimePausedException(plugin.installed.pluginId)
                holders.getOrPut(key) { Holder(createRuntime(plugin)) }.also { it.activeCalls++ }
            }
        return try {
            holder.runtime.invoke(method, request, context, timeout, validate)
        } catch (error: PluginRuntimeFatalException) {
            withContext(NonCancellable) { handleFatal(plugin.installed.pluginId, key, holder, error) }
            val cause = error.cause ?: error
            if (cause is kotlinx.coroutines.CancellationException) throw cause
            throw PluginRuntimeUnavailableException(plugin.installed.pluginId, cause)
        } finally {
            withContext(NonCancellable) { release(holder) }
        }
    }

    public suspend fun close(key: PluginRuntimeKeyV1) {
        val holder = mutex.withLock { holders.remove(key) }
        holder?.runtime?.close()
    }

    /** Called by platform memory-pressure handlers. Active Runtimes remain alive. */
    public suspend fun closeIdle() {
        val idle =
            mutex.withLock {
                holders.entries
                    .filter { it.value.activeCalls == 0 }
                    .onEach { holders.remove(it.key) }
                    .map(Map.Entry<PluginRuntimeKeyV1, Holder>::value)
            }
        idle.forEach { it.runtime.close() }
    }

    public suspend fun retry(pluginId: String) {
        mutex.withLock {
            failures.remove(pluginId)
            paused.remove(pluginId)
            mutableIssues.value = mutableIssues.value - pluginId
        }
    }

    public suspend fun close() {
        val snapshot =
            mutex.withLock {
                closed = true
                holders.values.toList().also { holders.clear() }
            }
        snapshot.forEach { holder -> holder.runtime.close() }
    }

    private suspend fun invokeTemporary(
        plugin: RunningPluginV1,
        context: PluginInvocationContextV1,
        method: String,
        request: JsonElement,
        timeout: PluginCallTimeoutV1,
        validate: (JsonElement) -> Unit,
    ): JsonElement {
        mutex.withLock {
            check(!closed) { "Plugin Runtime pool is closed" }
            if (plugin.installed.pluginId in paused) throw PluginRuntimePausedException(plugin.installed.pluginId)
        }
        val runtime = createRuntime(plugin)
        return try {
            runtime.invoke(method, request, context, timeout, validate)
        } catch (error: PluginRuntimeFatalException) {
            withContext(NonCancellable) { recordFailure(plugin.installed.pluginId, error.countsTowardPause) }
            val cause = error.cause ?: error
            if (cause is kotlinx.coroutines.CancellationException) throw cause
            throw PluginRuntimeUnavailableException(plugin.installed.pluginId, cause)
        } finally {
            withContext(NonCancellable) { runtime.close() }
        }
    }

    private fun createRuntime(plugin: RunningPluginV1): RuntimeHandle =
        RuntimeHandle(IsolatedPluginRuntime(plugin, loader, gateway, dispatcher))

    private suspend fun handleFatal(
        pluginId: String,
        key: PluginRuntimeKeyV1,
        holder: Holder,
        error: PluginRuntimeFatalException,
    ) {
        mutex.withLock {
            if (holders[key] === holder) holders.remove(key)
        }
        recordFailure(pluginId, error.countsTowardPause)
    }

    private suspend fun recordFailure(
        pluginId: String,
        countsTowardPause: Boolean,
    ) {
        mutex.withLock {
            val now = nowMillis()
            val recent = failures.getOrPut(pluginId, ::mutableListOf)
            recent.removeAll { now - it > FAILURE_WINDOW_MILLIS }
            if (countsTowardPause) recent += now
            val isPaused = recent.size >= FAILURE_LIMIT
            if (isPaused) paused += pluginId
            mutableIssues.value =
                mutableIssues.value +
                (
                    pluginId to
                        PluginRuntimeIssueV1(
                            pluginId = pluginId,
                            code = if (isPaused) "runtime.paused" else "runtime.fatal",
                            fatalFailures = recent.size,
                            paused = isPaused,
                        )
                )
        }
    }

    private suspend fun release(holder: Holder) {
        val close =
            mutex.withLock {
                holder.activeCalls--
                holder.lastUsedMillis = nowMillis()
                trimLocked()
            }
        close.forEach { it.runtime.close() }
    }

    private fun trimLocked(): List<Holder> {
        var heap = holders.values.sumOf { it.runtime.heapBytes }
        if (heap <= softHeapBudgetBytes) return emptyList()
        val result = mutableListOf<Holder>()
        holders.entries
            .filter { it.value.activeCalls == 0 }
            .sortedBy { it.value.lastUsedMillis }
            .forEach { (key, holder) ->
                if (heap <= softHeapBudgetBytes) return@forEach
                holders.remove(key)
                heap -= holder.runtime.heapBytes
                result += holder
            }
        return result
    }

    private fun validateInvocation(
        plugin: RunningPluginV1,
        key: PluginRuntimeKeyV1,
        context: PluginInvocationContextV1,
    ) {
        require(key.pluginId == plugin.installed.pluginId && key.packageHash == plugin.installed.packageHash) {
            "Runtime key does not match plugin"
        }
        require(context.metadata.pluginId == key.pluginId && context.metadata.packageHash == key.packageHash) {
            "Invocation context does not match Runtime key"
        }
        require(context.metadata.scope == key.scope) { "Invocation scope does not match Runtime key" }
        when (key.scope) {
            PluginInvocationScopeV1.Account -> require(key.identity == context.metadata.accountId) { "Account Runtime key mismatch" }

            PluginInvocationScopeV1.Guest -> require(key.identity == context.metadata.origin) { "Guest Runtime key mismatch" }

            PluginInvocationScopeV1.Detector,
            PluginInvocationScopeV1.Login,
            -> Unit
        }
    }

    private class Holder(
        val runtime: RuntimeHandle,
        var activeCalls: Int = 0,
        var lastUsedMillis: Long = 0,
    )

    private class RuntimeHandle(
        private val value: IsolatedPluginRuntime,
    ) {
        val heapBytes: Long
            get() = value.heapBytes

        suspend fun invoke(
            method: String,
            request: JsonElement,
            context: PluginInvocationContextV1,
            timeout: PluginCallTimeoutV1,
            validate: (JsonElement) -> Unit,
        ): JsonElement = value.invoke(method, request, context, timeout, validate)

        suspend fun close() = value.close()
    }
}

public class PluginRuntimePausedException(
    pluginId: String,
) : IllegalStateException("Plugin is paused for this process: $pluginId")

public class PluginRuntimeUnavailableException(
    pluginId: String,
    cause: Throwable,
) : IllegalStateException("Plugin Runtime is unavailable: $pluginId", cause)

private fun monotonicMillis(): () -> Long {
    val origin =
        kotlin.time.TimeSource.Monotonic
            .markNow()
    return { origin.elapsedNow().inWholeMilliseconds }
}

private const val FAILURE_LIMIT = 3
private const val FAILURE_WINDOW_MILLIS = 60_000L
private val HASH = Regex("[0-9a-f]{64}")
