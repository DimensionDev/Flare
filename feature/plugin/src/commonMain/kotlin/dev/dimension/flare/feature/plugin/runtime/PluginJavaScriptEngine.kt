package dev.dimension.flare.feature.plugin.runtime

import kotlinx.coroutines.CoroutineDispatcher

internal typealias PluginHostCall = suspend (invocationId: String, operation: String, arguments: String) -> String

internal interface PluginJavaScriptEngine {
    suspend fun evaluate(
        source: String,
        fileName: String,
        awaitResult: Boolean = false,
    ): String

    fun close()
}

internal expect suspend fun createPluginJavaScriptEngine(
    dispatcher: CoroutineDispatcher,
    hostCall: PluginHostCall,
): PluginJavaScriptEngine

internal class PluginJavaScriptException(
    message: String,
) : IllegalStateException(message)

internal class PluginJavaScriptUnavailableException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

internal const val PLUGIN_HOST_BINDING = "__flareHostCallV1"
