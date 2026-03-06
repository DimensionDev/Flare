package dev.dimension.flare.ui.render

import com.fleeksoft.ksoup.nodes.Element
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class UiRichTextTest {
    @Test
    fun serializerDoesNotPrettyPrintBrLines() {
        val uiRichText =
            UiRichText(
                data =
                    Element("body").apply {
                        appendText("Line 1")
                        appendChild(Element("br"))
                        appendText("Line 2")
                    },
                isRtl = false,
            )

        val encoded = Json.encodeToString(uiRichText)
        val decoded = Json.decodeFromString<UiRichText>(encoded)

        assertFalse(encoded.contains("\\n "))
        assertEquals("Line 1\nLine 2", decoded.raw)
    }
}
