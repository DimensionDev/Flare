package dev.dimension.flare.feature.plugin.runtime

import com.dokar.quickjs.ExperimentalQuickJsApi
import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.asyncFunction
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout

@OptIn(ExperimentalQuickJsApi::class)
internal actual suspend fun createPluginJavaScriptEngine(
    dispatcher: CoroutineDispatcher,
    hostCall: PluginHostCall,
): PluginJavaScriptEngine {
    val runtime = QuickJs.create(dispatcher)
    try {
        // quickjs-kt otherwise treats every detached rejection as an evaluation failure.
        val disableRejectionTracker =
            Linker.nativeLinker().downcallHandle(
                SymbolLookup.loaderLookup().findOrThrow("JS_SetHostPromiseRejectionTracker"),
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS),
            )
        runtime.withNativeContext {
            disableRejectionTracker.invokeWithArguments(MemorySegment.ofAddress(it.runtimeAddress), MemorySegment.NULL, MemorySegment.NULL)
        }
        runtime.asyncFunction<String>(PLUGIN_HOST_BINDING) { arguments ->
            require(arguments.size == 3 && arguments.all { it is String }) { "Invalid Host call" }
            hostCall(arguments[0] as String, arguments[1] as String, arguments[2] as String)
        }
        return object : PluginJavaScriptEngine {
            override suspend fun evaluate(
                source: String,
                fileName: String,
                awaitResult: Boolean,
            ): String =
                withContext(dispatcher) {
                    runtime.evaluate<Any?>(if (awaitResult) "await ($source)" else source, fileName) as? String
                        ?: throw PluginJavaScriptException("JavaScript must return a string")
                }

            override fun close() = runtime.close()
        }
    } catch (error: Throwable) {
        runtime.close()
        throw error
    }
}
