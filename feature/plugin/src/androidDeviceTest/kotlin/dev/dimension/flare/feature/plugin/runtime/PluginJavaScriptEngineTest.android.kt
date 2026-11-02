package dev.dimension.flare.feature.plugin.runtime

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers

internal actual suspend fun createTestPluginJavaScriptEngine(hostCall: PluginHostCall): PluginJavaScriptEngine =
    AndroidPluginJavaScriptEngine.create(InstrumentationRegistry.getInstrumentation().targetContext, Dispatchers.Default, hostCall)
