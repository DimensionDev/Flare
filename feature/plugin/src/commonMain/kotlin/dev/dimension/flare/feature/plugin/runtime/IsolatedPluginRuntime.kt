package dev.dimension.flare.feature.plugin.runtime

import dev.dimension.flare.feature.plugin.abi.DISABLE_DYNAMIC_CODE_PRELUDE
import dev.dimension.flare.feature.plugin.abi.PluginJsonV1
import dev.dimension.flare.feature.plugin.host.PluginCallTimeoutV1
import dev.dimension.flare.feature.plugin.host.PluginHostGateway
import dev.dimension.flare.feature.plugin.host.PluginInvocationContextV1
import dev.dimension.flare.feature.plugin.host.PluginInvocationMetadataV1
import dev.dimension.flare.feature.plugin.host.platformUuid
import dev.dimension.flare.feature.plugin.lifecycle.RunningPluginV1
import dev.dimension.flare.feature.plugin.manifest.PluginCatalogBundleV1
import dev.dimension.flare.feature.plugin.manifest.requiredMethods
import dev.dimension.flare.feature.plugin.manifest.toUiText
import dev.dimension.flare.feature.plugin.wire.PluginErrorV1
import dev.dimension.flare.feature.plugin.wire.requireValid
import dev.dimension.flare.ui.model.UiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal class IsolatedPluginRuntime(
    private val plugin: RunningPluginV1,
    private val loader: PluginPackageScriptLoader,
    private val gateway: PluginHostGateway,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val engineFactory: suspend (PluginHostCall) -> PluginJavaScriptEngine = { createPluginJavaScriptEngine(dispatcher, it) },
) {
    private val mutex = Mutex()
    private var engine: PluginJavaScriptEngine? = null
    private var poisoned: Boolean = false
    private val activeInvocation = MutableStateFlow<Invocation?>(null)

    suspend fun invoke(
        method: String,
        request: JsonElement,
        context: PluginInvocationContextV1,
        timeout: PluginCallTimeoutV1,
        validate: (JsonElement) -> Unit,
    ): JsonElement =
        withContext(dispatcher) {
            mutex.withLock {
                require(context.metadata.pluginId == plugin.installed.pluginId) { "Invocation plugin mismatch" }
                require(context.metadata.platformId == plugin.installed.manifest.platform.id) { "Invocation platform mismatch" }
                require(context.metadata.packageHash == plugin.installed.packageHash) { "Invocation package mismatch" }
                require(METHOD_PATH.matches(method)) { "Invalid plugin method" }
                require(method in plugin.installed.manifest.requiredMethods()) { "Plugin method is not declared" }
                val requestJson = request.toString()
                require(requestJson.encodeToByteArray().size <= MAX_INVOCATION_REQUEST_BYTES) { "Plugin request is too large" }
                if (poisoned) throw PluginRuntimeFatalException(IllegalStateException("Runtime generation is retired"), false)
                val invocationJob = currentCoroutineContext()[Job]
                try {
                    val runtime = engine ?: createRuntime().also { engine = it }
                    val contextJson = PluginJsonV1.encodeToString(PluginInvocationMetadataV1.serializer(), context.metadata)
                    val result =
                        withTimeout(timeout.millis) {
                            coroutineScope {
                                val invocation = Invocation(platformUuid(), context, timeout.millis, this)
                                activeInvocation.value = invocation
                                runtime.evaluate(
                                    "globalThis.__flareInvokeV1(${JsonPrimitive(invocation.id)}," +
                                        "${JsonPrimitive(method)},$requestJson,$contextJson)",
                                    "flare-invocation.js",
                                    awaitResult = true,
                                )
                            }
                        }
                    require(result.encodeToByteArray().size <= MAX_INVOCATION_RESPONSE_BYTES) { "Plugin response is too large" }
                    val value = decodeResult(result, plugin, context.metadata.locale)
                    validate(value)
                    value
                } catch (error: PluginCallException) {
                    throw error
                } catch (error: PluginRuntimeFatalException) {
                    throw error
                } catch (error: Throwable) {
                    poisoned = true
                    closeLocked()
                    val cancellation =
                        try {
                            invocationJob?.ensureActive()
                            null
                        } catch (cancelled: CancellationException) {
                            cancelled
                        }
                    throw PluginRuntimeFatalException(
                        cause = cancellation ?: error,
                        countsTowardPause =
                            cancellation == null &&
                                error !is PluginJavaScriptUnavailableException &&
                                (error !is CancellationException || error is TimeoutCancellationException),
                    )
                } finally {
                    activeInvocation.value = null
                }
            }
        }

    suspend fun close() {
        withContext(dispatcher) { mutex.withLock { closeLocked() } }
    }

    private suspend fun createRuntime(): PluginJavaScriptEngine {
        val runtime =
            engineFactory { invocationId, operation, arguments ->
                val invocation = activeInvocation.value
                check(invocation != null && invocation.id == invocationId) { "Host API called outside its invocation" }
                invocation.scope
                    .async {
                        // A catchable Host error must not cancel the invocation's coroutine scope.
                        runCatching { gateway.call(operation, arguments, invocation.context, invocation.timeoutMillis) }
                    }.await()
                    .getOrThrow()
            }
        try {
            val source = loader.load(plugin)
            withTimeout(INITIALIZATION_TIMEOUT_MILLIS) {
                runtime.evaluate(RUNTIME_PRELUDE + DISABLE_DYNAMIC_CODE_PRELUDE + source + "\n;''", "plugin.js")
                check(runtime.evaluate("String(globalThis.__flareValidateRegistrationV1())", "flare-registration.js") == "true")
            }
            return runtime
        } catch (error: Throwable) {
            runtime.close()
            throw error
        }
    }

    private fun closeLocked() {
        activeInvocation.value = null
        engine?.close()
        engine = null
    }

    private class Invocation(
        val id: String,
        val context: PluginInvocationContextV1,
        val timeoutMillis: Long,
        val scope: CoroutineScope,
    )
}

public class PluginCallException(
    public val error: PluginErrorV1,
    resolvedMessage: String? = null,
) : IllegalStateException(resolvedMessage ?: error.message.value ?: error.message.fallback ?: error.code.name)

internal class PluginRuntimeFatalException(
    cause: Throwable,
    val countsTowardPause: Boolean,
) : IllegalStateException("Plugin Runtime failed", cause)

private fun decodeResult(
    value: String,
    plugin: RunningPluginV1,
    locale: String,
): JsonElement {
    val objectValue = PluginJsonV1.parseToJsonElement(value).jsonObject
    return when (objectValue["ok"]?.jsonPrimitive?.booleanOrNull) {
        true -> {
            requireNotNull(objectValue["value"]) { "Plugin returned undefined" }
        }

        false -> {
            objectValue["fatal"]?.jsonPrimitive?.content?.let { throw PluginJavaScriptException(it) }
            val error = PluginJsonV1.decodeFromJsonElement(PluginErrorV1.serializer(), requireNotNull(objectValue["error"]))
            error.message.requireValid()
            require(error.retryAfterSeconds == null || error.retryAfterSeconds in 0..604_800) { "Invalid retry delay" }
            require(error.remoteCode == null || error.remoteCode.length <= 512) { "Remote error code is too long" }
            val message =
                error.message.value
                    ?: PluginCatalogBundleV1(
                        pluginId = plugin.installed.pluginId,
                        defaultLocale = plugin.installed.manifest.defaultLocale,
                        catalogs = plugin.installed.catalogs,
                    ).resolve(
                        error.message.toUiText(plugin.installed.pluginId) as UiText.ExternalRef,
                        locale,
                    )
            throw PluginCallException(error, message)
        }

        null -> {
            error("Invalid plugin result envelope")
        }
    }
}

private const val INITIALIZATION_TIMEOUT_MILLIS = 2_000L
private const val MAX_INVOCATION_REQUEST_BYTES = 1024 * 1_024
private const val MAX_INVOCATION_RESPONSE_BYTES = 5 * 1_024 * 1_024
private val METHOD_PATH = Regex("[A-Za-z][A-Za-z0-9_-]*(?:\\.[A-Za-z][A-Za-z0-9_-]*)+")

internal val RUNTIME_PRELUDE =
    """
    (() => {
      const parse = JSON.parse.bind(JSON);
      const stringify = JSON.stringify.bind(JSON);
      const freeze = Object.freeze.bind(Object);
      const keys = Object.keys.bind(Object);
      const owns = Function.call.bind(Object.prototype.hasOwnProperty);
      const then = Function.call.bind(Promise.prototype.then);
      const all = Promise.all.bind(Promise);
      const push = Function.call.bind(Array.prototype.push);
      const slice = Function.call.bind(Array.prototype.slice);
      const nativeHostCall = globalThis.__flareHostCallV1;
      const ignore = () => {};
      let registration;
      let invocation;
      const deepFreeze = (value, seen = new Set()) => {
        if (value === null || (typeof value !== "object" && typeof value !== "function") || seen.has(value)) return value;
        seen.add(value);
        for (const key of keys(value)) deepFreeze(value[key], seen);
        return freeze(value);
      };
      const hostCall = (operation, value = {}) => {
        const call = invocation;
        const task = (async () => {
          if (!call || call.closed) throw new Error("Host API called outside an invocation");
          // Register this task before starting native Host work.
          await undefined;
          const envelope = parse(await nativeHostCall(call.id, operation, stringify(value)));
          if (!envelope || envelope.ok !== true) {
            const error = new Error(envelope?.error?.message || "Host operation failed");
            error.code = envelope?.error?.code || "host.unavailable";
            throw error;
          }
          return envelope.value;
        })();
        if (call && !call.closed) push(call.pending, then(task, ignore, ignore));
        return task;
      };
      const facade = {
        http: { request: request => hostCall("http.request", request) },
        credential: {
          read: () => hostCall("credential.read"),
          replace: value => hostCall("credential.replace", { value }),
        },
        crypto: {
          randomHex: size => hostCall("crypto.randomHex", { size }),
          uuid: () => hostCall("crypto.uuid"),
          sha256: value => hostCall("crypto.sha256", { value }),
        },
        locale: { current: () => hostCall("locale.current") },
        error: value => {
          const error = new Error(value?.message?.value || value?.message?.fallback || value?.code || "Plugin error");
          Object.defineProperty(error, "__flarePluginErrorV1", { value, enumerable: false });
          return error;
        },
      };
      Object.defineProperty(globalThis, "flare", { configurable: false, writable: false, value: deepFreeze(facade) });
      Object.defineProperty(globalThis, "definePlugin", {
        configurable: false,
        writable: false,
        value(value) {
          if (registration !== undefined) throw new Error("definePlugin may only be called once");
          if (value === null || typeof value !== "object") throw new Error("Invalid plugin registration");
          registration = deepFreeze(value);
        },
      });
      Object.defineProperty(globalThis, "__flareValidateRegistrationV1", {
        configurable: false,
        writable: false,
        value() {
          if (registration === undefined) throw new Error("plugin.js did not call definePlugin");
          return true;
        },
      });
      Object.defineProperty(globalThis, "__flareInvokeV1", {
        configurable: false,
        writable: false,
        async value(invocationId, method, request, context) {
          const call = { id: invocationId, pending: [], closed: false };
          invocation = call;
          try {
            let owner = registration;
            const parts = method.split(".");
            for (let index = 0; index < parts.length - 1; index++) {
              if (!owns(owner, parts[index])) throw new Error(`Missing plugin method ${'$'}{method}`);
              owner = owner?.[parts[index]];
            }
            if (!owns(owner, parts[parts.length - 1])) throw new Error(`Missing plugin method ${'$'}{method}`);
            const fn = owner?.[parts[parts.length - 1]];
            if (typeof fn !== "function") throw new Error(`Missing plugin method ${'$'}{method}`);
            const value = await fn.call(owner, deepFreeze(request), deepFreeze(context));
            if (value === undefined) throw new Error(`Plugin method ${'$'}{method} returned undefined`);
            return stringify({ ok: true, value });
          } catch (error) {
            if (error?.__flarePluginErrorV1 !== undefined) {
              return stringify({ ok: false, error: error.__flarePluginErrorV1 });
            }
            return stringify({ ok: false, fatal: String(error?.stack || error).slice(0, 4096) });
          } finally {
            try {
              let completed = 0;
              while (completed < call.pending.length) {
                const pending = slice(call.pending, completed);
                completed = call.pending.length;
                await all(pending);
              }
            } finally {
              call.closed = true;
              if (invocation === call) invocation = undefined;
            }
          }
        },
      });
    })();
    """.trimIndent() + "\n"
