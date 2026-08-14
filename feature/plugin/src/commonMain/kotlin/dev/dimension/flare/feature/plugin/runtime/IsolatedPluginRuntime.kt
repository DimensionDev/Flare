package dev.dimension.flare.feature.plugin.runtime

import com.dokar.quickjs.ExperimentalQuickJsApi
import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.asyncFunction
import dev.dimension.flare.feature.plugin.abi.DISABLE_DYNAMIC_CODE_PRELUDE
import dev.dimension.flare.feature.plugin.abi.PluginJsonV1
import dev.dimension.flare.feature.plugin.host.PluginCallTimeoutV1
import dev.dimension.flare.feature.plugin.host.PluginHostGateway
import dev.dimension.flare.feature.plugin.host.PluginInvocationContextV1
import dev.dimension.flare.feature.plugin.host.PluginInvocationMetadataV1
import dev.dimension.flare.feature.plugin.lifecycle.RunningPluginV1
import dev.dimension.flare.feature.plugin.manifest.PluginCatalogBundleV1
import dev.dimension.flare.feature.plugin.manifest.requiredMethods
import dev.dimension.flare.feature.plugin.manifest.toUiText
import dev.dimension.flare.feature.plugin.wire.PluginErrorV1
import dev.dimension.flare.feature.plugin.wire.requireValid
import dev.dimension.flare.ui.model.UiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
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
) {
    private val mutex = Mutex()
    private var quickJs: QuickJs? = null
    private var poisoned: Boolean = false
    private var activeContext: PluginInvocationContextV1? = null
    private var activeTimeoutMillis: Long = PluginCallTimeoutV1.Normal.millis

    var heapBytes: Long = 0
        private set

    @OptIn(ExperimentalQuickJsApi::class, InternalCoroutinesApi::class)
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
                var interruptHandle: DisposableHandle? = null
                try {
                    val runtime = quickJs ?: createRuntime().also { quickJs = it }
                    interruptHandle =
                        invocationJob?.invokeOnCompletion(onCancelling = true, invokeImmediately = true) { cause ->
                            if (cause != null) runtime.interruptEvaluation()
                        }
                    val contextJson = PluginJsonV1.encodeToString(PluginInvocationMetadataV1.serializer(), context.metadata)
                    activeContext = context
                    activeTimeoutMillis = timeout.millis
                    runtime.evaluationTimeoutMillis = timeout.millis
                    val result =
                        withTimeout(timeout.millis) {
                            runtime.evaluate<String>(
                                "await globalThis.__flareInvokeV1(${JsonPrimitive(method)},$requestJson,$contextJson)",
                                "flare-invocation.js",
                            )
                        }
                    require(result.encodeToByteArray().size <= MAX_INVOCATION_RESPONSE_BYTES) { "Plugin response is too large" }
                    val value = decodeResult(result, plugin, context.metadata.locale)
                    validate(value)
                    heapBytes = runtime.memoryUsage.memoryUsedSize
                    value
                } catch (error: PluginCallException) {
                    heapBytes = quickJs?.memoryUsage?.memoryUsedSize ?: 0
                    throw error
                } catch (error: PluginRuntimeFatalException) {
                    throw error
                } catch (error: Throwable) {
                    poisoned = true
                    closeLocked()
                    val cancellation = invocationJob?.takeIf { it.isCancelled }?.getCancellationException()
                    throw PluginRuntimeFatalException(
                        cause = cancellation ?: error,
                        countsTowardPause =
                            cancellation == null &&
                                (error !is CancellationException || error is TimeoutCancellationException),
                    )
                } finally {
                    interruptHandle?.dispose()
                    activeContext = null
                    activeTimeoutMillis = PluginCallTimeoutV1.Normal.millis
                }
            }
        }

    suspend fun close() {
        withContext(dispatcher) { mutex.withLock { closeLocked() } }
    }

    private suspend fun createRuntime(): QuickJs {
        val runtime = QuickJs.create(dispatcher)
        try {
            runtime.memoryLimit = RUNTIME_MEMORY_BYTES
            runtime.maxStackSize = RUNTIME_STACK_BYTES
            runtime.evaluationTimeoutMillis = INITIALIZATION_TIMEOUT_MILLIS
            runtime.asyncFunction<String>(HOST_BINDING) { arguments ->
                val context = activeContext ?: error("Host API called outside an invocation")
                require(arguments.size == 2 && arguments[0] is String && arguments[1] is String) { "Invalid Host call" }
                gateway.call(
                    operation = arguments[0] as String,
                    argumentsJson = arguments[1] as String,
                    context = context,
                    callTimeoutMillis = activeTimeoutMillis,
                )
            }
            val source = loader.load(plugin)
            withTimeout(INITIALIZATION_TIMEOUT_MILLIS) {
                runtime.evaluate<Unit>(RUNTIME_PRELUDE + DISABLE_DYNAMIC_CODE_PRELUDE + source, "plugin.js")
                runtime.evaluate<Boolean>("globalThis.__flareValidateRegistrationV1()", "flare-registration.js")
            }
            heapBytes = runtime.memoryUsage.memoryUsedSize
            return runtime
        } catch (error: Throwable) {
            runtime.close()
            throw error
        }
    }

    private fun closeLocked() {
        quickJs?.close()
        quickJs = null
        heapBytes = 0
    }
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

private const val HOST_BINDING = "__flareHostCallV1"
private const val RUNTIME_MEMORY_BYTES = 64L * 1024 * 1024
private const val RUNTIME_STACK_BYTES = 512L * 1024
private const val INITIALIZATION_TIMEOUT_MILLIS = 2_000L
private const val MAX_INVOCATION_REQUEST_BYTES = 1024 * 1_024
private const val MAX_INVOCATION_RESPONSE_BYTES = 5 * 1_024 * 1_024
private val METHOD_PATH = Regex("[A-Za-z][A-Za-z0-9_-]*(?:\\.[A-Za-z][A-Za-z0-9_-]*)+")

private val RUNTIME_PRELUDE =
    """
    (() => {
      const parse = JSON.parse.bind(JSON);
      const stringify = JSON.stringify.bind(JSON);
      const freeze = Object.freeze.bind(Object);
      const keys = Object.keys.bind(Object);
      const owns = Function.call.bind(Object.prototype.hasOwnProperty);
      let registration;
      const deepFreeze = (value, seen = new Set()) => {
        if (value === null || (typeof value !== "object" && typeof value !== "function") || seen.has(value)) return value;
        seen.add(value);
        for (const key of keys(value)) deepFreeze(value[key], seen);
        return freeze(value);
      };
      const hostCall = async (operation, value = {}) => {
        const envelope = parse(await globalThis.__flareHostCallV1(operation, stringify(value)));
        if (!envelope || envelope.ok !== true) {
          const error = new Error(envelope?.error?.message || "Host operation failed");
          error.code = envelope?.error?.code || "host.unavailable";
          throw error;
        }
        return envelope.value;
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
        async value(method, request, context) {
          let owner = registration;
          const parts = method.split(".");
          for (let index = 0; index < parts.length - 1; index++) {
            if (!owns(owner, parts[index])) throw new Error(`Missing plugin method ${'$'}{method}`);
            owner = owner?.[parts[index]];
          }
          if (!owns(owner, parts[parts.length - 1])) throw new Error(`Missing plugin method ${'$'}{method}`);
          const fn = owner?.[parts[parts.length - 1]];
          if (typeof fn !== "function") throw new Error(`Missing plugin method ${'$'}{method}`);
          try {
            const value = await fn.call(owner, deepFreeze(request), deepFreeze(context));
            if (value === undefined) throw new Error(`Plugin method ${'$'}{method} returned undefined`);
            return stringify({ ok: true, value });
          } catch (error) {
            if (error?.__flarePluginErrorV1 !== undefined) {
              return stringify({ ok: false, error: error.__flarePluginErrorV1 });
            }
            throw error;
          }
        },
      });
    })();
    """.trimIndent() + "\n"
