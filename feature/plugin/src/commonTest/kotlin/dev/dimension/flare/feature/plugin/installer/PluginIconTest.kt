package dev.dimension.flare.feature.plugin.installer

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails

class PluginIconTest {
    @Test
    fun validatesAllSupportedFormatsWithoutChangingTheirBytes() {
        pluginIconFixtures.forEach { (extension, bytes) ->
            val original = bytes.copyOf()
            assertEquals(extension, pluginIconExtension(bytes))
            validatePluginIconImage(bytes)
            assertContentEquals(original, bytes)
        }
    }

    @Test
    fun rejectsSvgUnknownCorruptAndOversizedIcons() {
        listOf("<svg xmlns=\"http://www.w3.org/2000/svg\"/>".encodeToByteArray(), "<html/>".encodeToByteArray(), byteArrayOf()).forEach {
            assertFails { pluginIconExtension(it) }
        }
        assertFails { pluginIconExtension(ByteArray(FppLimits.MAX_ICON_BYTES.toInt() + 1)) }
        pluginIconFixtures.values.forEach { assertFails { validatePluginIconImage(it.copyOf(12)) } }
        assertFails { requirePluginIconDimensions(1025, 32) }
        assertFails { requirePluginIconDimensions(32, 0) }
    }
}
