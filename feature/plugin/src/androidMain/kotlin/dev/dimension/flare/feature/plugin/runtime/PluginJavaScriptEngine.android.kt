package dev.dimension.flare.feature.plugin.runtime

import android.content.Context
import androidx.javascriptengine.IsolateTerminatedException
import androidx.javascriptengine.JavaScriptIsolate
import androidx.javascriptengine.JavaScriptSandbox
import androidx.javascriptengine.MemoryLimitExceededException
import androidx.javascriptengine.Message
import androidx.javascriptengine.SandboxDeadException
import androidx.javascriptengine.TerminationInfo
import com.google.common.util.concurrent.ListenableFuture
import dev.dimension.flare.di.koinGet
import dev.dimension.flare.feature.plugin.abi.PluginJsonV1
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.concurrent.ExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal actual suspend fun createPluginJavaScriptEngine(
    dispatcher: CoroutineDispatcher,
    hostCall: PluginHostCall,
): PluginJavaScriptEngine = AndroidPluginJavaScriptEngine.create(koinGet(), dispatcher, hostCall)

// Android permits one sandbox connection per app; each runtime still owns a separate isolate.
internal class PluginAndroidSandboxGeneration(
    val sandbox: JavaScriptSandbox,
) {
    val active = AtomicBoolean(true)
}

internal object PluginAndroidSandbox {
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var connection: ListenableFuture<JavaScriptSandbox>? = null
    private var generation: PluginAndroidSandboxGeneration? = null

    suspend fun acquire(context: Context): PluginAndroidSandboxGeneration =
        mutex.withLock {
            generation?.let {
                if (it.active.get()) return@withLock it
                generation = null
                connection = null
                it.sandbox.close()
            }
            if (!JavaScriptSandbox.isSupported()) {
                throw PluginJavaScriptUnavailableException("Plugin JavaScript is unavailable; update Android System WebView")
            }
            try {
                val future =
                    connection ?: JavaScriptSandbox.createConnectedInstanceAsync(context.applicationContext).also { connection = it }
                // The shared connection remains owned here even if its first caller cancels.
                val sandbox = future.await(cancelFuture = false)
                val required =
                    listOf(
                        JavaScriptSandbox.JS_FEATURE_MESSAGE_PORTS,
                        JavaScriptSandbox.JS_FEATURE_PROMISE_RETURN,
                        JavaScriptSandbox.JS_FEATURE_EVALUATE_WITHOUT_TRANSACTION_LIMIT,
                    )
                if (required.any { !sandbox.isFeatureSupported(it) }) {
                    connection = null
                    sandbox.close()
                    throw PluginJavaScriptUnavailableException("Plugin JavaScript requires a newer Android System WebView")
                }
                PluginAndroidSandboxGeneration(sandbox).also { generation = it }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                connection = null
                if (error is PluginJavaScriptUnavailableException) throw error
                throw PluginJavaScriptUnavailableException("Cannot connect to the plugin JavaScript sandbox", error)
            }
        }

    fun invalidate(retired: PluginAndroidSandboxGeneration) {
        if (!retired.active.compareAndSet(true, false)) return
        scope.launch {
            mutex.withLock {
                if (generation === retired) {
                    generation = null
                    connection = null
                    retired.sandbox.close()
                }
            }
        }
    }
}

internal class AndroidPluginJavaScriptEngine private constructor(
    private val generation: PluginAndroidSandboxGeneration,
    private val isolate: JavaScriptIsolate,
    dispatcher: CoroutineDispatcher,
    private val hostCall: PluginHostCall,
) : PluginJavaScriptEngine {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val closed = AtomicBoolean(false)
    private val failure = AtomicReference<Throwable?>(null)
    private val port =
        isolate.createMessageChannel("flare-host", dispatcher.asExecutor()) { message ->
            scope.launch { receive(message) }
        }

    init {
        isolate.addOnTerminatedCallback(dispatcher.asExecutor()) { info ->
            val error =
                if (info.status == TerminationInfo.STATUS_MEMORY_LIMIT_EXCEEDED) {
                    PluginJavaScriptException(info.message)
                } else {
                    PluginJavaScriptUnavailableException("Plugin JavaScript sandbox stopped: ${info.message}")
                }
            failure.compareAndSet(null, error)
            scope.cancel()
            if (info.status == TerminationInfo.STATUS_SANDBOX_DEAD || info.status == TerminationInfo.STATUS_MEMORY_LIMIT_EXCEEDED) {
                PluginAndroidSandbox.invalidate(generation)
            }
        }
    }

    override suspend fun evaluate(
        source: String,
        fileName: String,
        awaitResult: Boolean,
    ): String {
        failure.get()?.let { throw it }
        if (!generation.active.get()) throw PluginJavaScriptUnavailableException("Plugin JavaScript sandbox stopped")
        check(!closed.get()) { "JavaScript engine is closed" }
        try {
            return isolate.evaluateJavaScriptAsync("$source\n//# sourceURL=$fileName").await()
        } catch (error: IsolateTerminatedException) {
            if (error is MemoryLimitExceededException) throw PluginJavaScriptException(error.message.orEmpty())
            if (error is SandboxDeadException) PluginAndroidSandbox.invalidate(generation)
            throw PluginJavaScriptUnavailableException("Plugin JavaScript sandbox stopped", error)
        }
    }

    private suspend fun receive(message: Message) {
        try {
            require(message.type == Message.TYPE_STRING) { "Invalid Host message" }
            val request = PluginJsonV1.parseToJsonElement(message.string).jsonObject
            val id = request.getValue("id").jsonPrimitive.also { require(it.isString) }
            val arguments = request["arguments"] as? JsonArray ?: error("Invalid Host arguments")
            require(arguments.size == 3 && arguments.all { it is JsonPrimitive && it.isString }) { "Invalid Host call" }
            val reply =
                try {
                    val value =
                        hostCall(arguments[0].jsonPrimitive.content, arguments[1].jsonPrimitive.content, arguments[2].jsonPrimitive.content)
                    buildJsonObject {
                        put("id", id)
                        put("ok", true)
                        put("value", value)
                    }
                } catch (error: Throwable) {
                    // A Host request timeout must reject its Promise while the bridge is still active.
                    currentCoroutineContext().ensureActive()
                    buildJsonObject {
                        put("id", id)
                        put("ok", false)
                        put("error", error.message ?: "Host operation failed")
                    }
                }
            if (!closed.get()) port.postMessage(Message.createStringMessage(reply.toString()))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            failure.compareAndSet(null, error)
            close()
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        scope.cancel()
        port.close()
        isolate.close()
    }

    companion object {
        suspend fun create(
            context: Context,
            dispatcher: CoroutineDispatcher,
            hostCall: PluginHostCall,
        ): PluginJavaScriptEngine {
            val generation = PluginAndroidSandbox.acquire(context)
            var isolate: JavaScriptIsolate? = null
            var engine: AndroidPluginJavaScriptEngine? = null
            try {
                isolate = generation.sandbox.createIsolate()
                engine = AndroidPluginJavaScriptEngine(generation, isolate, dispatcher, hostCall)
                engine.evaluate(HOST_BRIDGE, "flare-host.js", awaitResult = true)
                return engine
            } catch (error: Throwable) {
                engine?.close() ?: isolate?.close()
                if (error is CancellationException) throw error
                PluginAndroidSandbox.invalidate(generation)
                throw PluginJavaScriptUnavailableException("Cannot initialize the plugin JavaScript sandbox", error)
            }
        }
    }
}

private suspend fun <T> ListenableFuture<T>.await(cancelFuture: Boolean = true): T =
    suspendCancellableCoroutine { continuation ->
        addListener({
            try {
                continuation.resume(get())
            } catch (error: ExecutionException) {
                continuation.resumeWithException(error.cause ?: error)
            } catch (error: Throwable) {
                continuation.resumeWithException(error)
            }
        }, Runnable::run)
        if (cancelFuture) continuation.invokeOnCancellation { cancel(true) }
    }

private val HOST_BRIDGE =
    """
    (async () => {
      const port = await android.getNamedPort('flare-host');
      const parse = JSON.parse.bind(JSON);
      const stringify = JSON.stringify.bind(JSON);
      const PromiseType = Promise;
      const pending = new Map();
      const get = Function.call.bind(Map.prototype.get);
      const set = Function.call.bind(Map.prototype.set);
      const remove = Function.call.bind(Map.prototype.delete);
      const send = port.postMessage.bind(port);
      let nextId = 0;
      port.onmessage = event => {
        const message = parse(event.data);
        const task = get(pending, message.id);
        if (!task) return;
        remove(pending, message.id);
        if (message.ok) task.resolve(message.value);
        else task.reject(new Error(message.error));
      };
      Object.defineProperty(globalThis, '__flareHostCallV1', {
        value: (...args) => new PromiseType((resolve, reject) => {
          if (args.length !== 3 || args.some(value => typeof value !== 'string')) {
            reject(new Error('Invalid Host call'));
            return;
          }
          const id = String(nextId++);
          set(pending, id, { resolve, reject });
          send(stringify({ id, arguments: args }));
        }),
      });
      return '';
    })()
    """.trimIndent()
