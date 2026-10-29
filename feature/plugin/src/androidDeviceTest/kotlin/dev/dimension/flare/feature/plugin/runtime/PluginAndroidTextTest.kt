package dev.dimension.flare.feature.plugin.runtime

import dev.dimension.flare.ui.model.UiText
import dev.dimension.flare.ui.model.UiTextArgument
import kotlin.test.Test
import kotlin.test.assertEquals

class PluginAndroidTextTest {
    @Test
    fun externalTextInitializesOnAndroid() {
        val text =
            UiText.ExternalRef(
                namespace = "test",
                key = "welcome",
                fallback = "Hello, {name}",
                args = mapOf("name" to UiTextArgument.StringValue("Flare")),
            )

        assertEquals("Hello, Flare", text.fallbackText())
    }
}
