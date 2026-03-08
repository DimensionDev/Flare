package dev.dimension.flare.ui.screen.rss

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

class RssSourcesScreenTest {
    @Test
    fun writeOpmlToStreamWritesOpmlContent() {
        val outputStream = ByteArrayOutputStream()
        val opmlContent = """<?xml version="1.0" encoding="UTF-8"?><opml version="2.0"></opml>"""

        writeOpmlToStream(outputStream, opmlContent)
        val result = outputStream.toString(StandardCharsets.UTF_8)

        assertEquals(opmlContent, result)
    }
}
