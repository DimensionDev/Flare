package dev.dimension.flare.feature.plugin.runtime

import kotlinx.coroutines.Dispatchers

internal actual suspend fun createTestPluginJavaScriptEngine(hostCall: PluginHostCall): PluginJavaScriptEngine =
    createPluginJavaScriptEngine(Dispatchers.Default, hostCall)
