@file:OptIn(
    kotlinx.cinterop.ExperimentalForeignApi::class,
    kotlinx.cinterop.BetaInteropApi::class,
    kotlin.experimental.ExperimentalNativeApi::class,
)

package dev.dimension.flare.feature.plugin.runtime

import kotlinx.cinterop.autoreleasepool
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import platform.CoreFoundation.CFRunLoopPerformBlock
import platform.CoreFoundation.CFRunLoopRun
import platform.CoreFoundation.CFRunLoopStop
import platform.CoreFoundation.CFRunLoopWakeUp
import platform.CoreFoundation.kCFRunLoopDefaultMode
import platform.Foundation.NSDefaultRunLoopMode
import platform.Foundation.NSPort
import platform.Foundation.NSRunLoop
import platform.Foundation.NSThread
import platform.Foundation.NSURL
import platform.JavaScriptCore.JSContext
import platform.JavaScriptCore.JSValue
import platform.JavaScriptCore.JSVirtualMachine
import platform.JavaScriptCore.setValue
import kotlin.native.ref.WeakReference

internal actual suspend fun createPluginJavaScriptEngine(
    dispatcher: CoroutineDispatcher,
    hostCall: PluginHostCall,
): PluginJavaScriptEngine = ApplePluginJavaScriptEngine(dispatcher, hostCall).also { it.start() }

private class ApplePluginJavaScriptEngine(
    dispatcher: CoroutineDispatcher,
    private val hostCall: PluginHostCall,
) : PluginJavaScriptEngine {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val closed = MutableStateFlow(false)
    private val ready = CompletableDeferred<Unit>()
    private val runLoop = MutableStateFlow<NSRunLoop?>(null)
    private val evaluations = MutableStateFlow<Set<CompletableDeferred<String>>>(emptySet())

    // All JSContext/JSValue access and disposal belongs to the evaluator thread.
    private var context: JSContext? = null
    private var observe: JSValue? = null
    private var nextCallId = 0L
    private val hostReplies = mutableMapOf<Long, Pair<JSValue?, JSValue?>>()

    suspend fun start() {
        NSThread(block = {
            autoreleasepool {
                val loop = NSRunLoop.currentRunLoop
                val port = NSPort.port()
                loop.addPort(port, forMode = NSDefaultRunLoopMode)
                try {
                    val js = JSContext(JSVirtualMachine())
                    context = js
                    observe =
                        js.evaluateScript(
                            """
                            (() => {
                              const resolve = Promise.resolve.bind(Promise);
                              const then = Function.call.bind(Promise.prototype.then);
                              return (value, done) => then(resolve(value),
                                result => done(typeof result === 'string',
                                  typeof result === 'string' ? result : 'JavaScript must return a string'),
                                error => done(false, String(error?.stack || error)));
                            })()
                            """.trimIndent(),
                        )
                    val owner = WeakReference(this)
                    val nativeHost: () -> JSValue? = { owner.get()?.callHost() }
                    js.globalObject!!.setValue(nativeHost, forProperty = PLUGIN_HOST_BINDING)
                    runLoop.value = loop
                    ready.complete(Unit)
                    if (!closed.value) CFRunLoopRun()
                } catch (error: Throwable) {
                    ready.completeExceptionally(error)
                    evaluations.value.forEach { it.completeExceptionally(error) }
                } finally {
                    hostReplies.clear()
                    observe = null
                    context = null
                    runLoop.value = null
                    loop.removePort(port, forMode = NSDefaultRunLoopMode)
                    port.invalidate()
                }
            }
        }).apply { name = "Flare plugin JavaScriptCore" }.start()
        try {
            ready.await()
        } catch (error: Throwable) {
            close()
            throw error
        }
    }

    override suspend fun evaluate(
        source: String,
        fileName: String,
        awaitResult: Boolean,
    ): String {
        val result = CompletableDeferred<String>()
        evaluations.update { it + result }
        try {
            check(!closed.value) { "JavaScript engine is closed" }
            post {
                if (!closed.value && result.isActive) {
                    try {
                        val js = checkNotNull(context)
                        js.exception = null
                        val value = js.evaluateScript(source, withSourceURL = NSURL.URLWithString(fileName))
                        js.exception?.let { throw PluginJavaScriptException(it.toString_().orEmpty()) }
                        if (awaitResult) {
                            val done: (Boolean, String?) -> Unit = { success, text ->
                                if (success) {
                                    result.complete(text.orEmpty())
                                } else {
                                    result.completeExceptionally(PluginJavaScriptException(text.orEmpty()))
                                }
                            }
                            observe!!.callWithArguments(listOf(value, done))
                            js.exception?.let { throw PluginJavaScriptException(it.toString_().orEmpty()) }
                        } else {
                            if (value?.isString != true) throw PluginJavaScriptException("JavaScript must return a string")
                            result.complete(value.toString_().orEmpty())
                        }
                    } catch (error: Throwable) {
                        result.completeExceptionally(error)
                    }
                }
            }
            return result.await()
        } finally {
            result.cancel()
            evaluations.update { it - result }
        }
    }

    private fun callHost(): JSValue? {
        val js = JSContext.currentContext() ?: return null
        val arguments = JSContext.currentArguments().orEmpty()
        val valid = arguments.size == 3 && arguments.all { it is JSValue && it.isString }
        val strings = if (valid) arguments.map { (it as JSValue).toString_().orEmpty() } else emptyList()
        return JSValue.valueWithNewPromiseInContext(js, fromExecutor = { resolve, reject ->
            if (!valid || closed.value) {
                reject?.callWithArguments(listOf("Invalid Host call"))
            } else {
                val id = nextCallId++
                hostReplies[id] = resolve to reject
                scope.launch {
                    val response = runCatching { hostCall(strings[0], strings[1], strings[2]) }
                    post {
                        val reply = hostReplies.remove(id)
                        if (!closed.value && reply != null) {
                            response.fold(
                                onSuccess = { reply.first?.callWithArguments(listOf(it)) },
                                onFailure = { reply.second?.callWithArguments(listOf(it.message ?: "Host operation failed")) },
                            )
                        }
                    }
                }
            }
        })
    }

    private fun post(action: () -> Unit) {
        val loop = runLoop.value?.getCFRunLoop() ?: return
        CFRunLoopPerformBlock(loop, kCFRunLoopDefaultMode) { autoreleasepool(action) }
        CFRunLoopWakeUp(loop)
    }

    override fun close() {
        if (!closed.compareAndSet(expect = false, update = true)) return
        scope.cancel()
        evaluations.value.forEach { it.completeExceptionally(PluginJavaScriptException("JavaScript engine is closed")) }
        // shortcut: public JSC cannot interrupt a loop; its worker survives until JS returns or process exit.
        post { runLoop.value?.let { CFRunLoopStop(it.getCFRunLoop()) } }
    }
}
