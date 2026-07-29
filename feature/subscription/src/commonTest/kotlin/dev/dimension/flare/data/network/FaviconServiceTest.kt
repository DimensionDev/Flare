package dev.dimension.flare.data.network

import kotlin.test.Test
import kotlin.test.assertEquals

class FaviconServiceTest {
    @Test
    fun standardFaviconIsPreferredOverLargerAppleTouchIcon() {
        val html =
            """
            <link rel="icon" href="/favicon.ico">
            <link rel="apple-touch-icon" sizes="192x192" href="/apple-touch-icon.png">
            """.trimIndent()

        assertEquals(
            "https://x.com/favicon.ico",
            findFaviconUrl("https://x.com", html),
        )
    }

    @Test
    fun appleTouchIconRemainsAvailableAsFallback() {
        val html = """<link rel="apple-touch-icon" sizes="192x192" href="/apple-touch-icon.png">"""

        assertEquals(
            "https://example.com/apple-touch-icon.png",
            findFaviconUrl("https://example.com", html),
        )
    }
}
