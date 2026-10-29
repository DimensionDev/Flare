package dev.dimension.flare.data.network

import kotlin.test.Test
import kotlin.test.assertEquals

class WebsiteIconsTest {
    @Test
    fun resolvesRelativeBaseAndProtocolRelativeLinksWithMetadata() {
        val icons =
            websiteIcons(
                "https://example.com/docs/page",
                """
                <head><base href="../assets/">
                <link rel="SHORTCUT ICON" href="logo.webp" type="image/webp" sizes="32x32 64x64" media="(prefers-color-scheme: dark)">
                <link rel="apple-touch-icon" href="//cdn.example.com/apple.png">
                <link rel="stylesheet" href="style.css">
                <link rel="icon" href="data:image/png;base64,AAAA">
                </head><body><link rel="icon" href="/not-in-head.png"></body>
                """.trimIndent(),
            )
        assertEquals(listOf("https://example.com/assets/logo.webp", "https://cdn.example.com/apple.png"), icons.map { it.url })
        assertEquals("shortcut icon", icons.first().rel)
        assertEquals("image/webp", icons.first().type)
        assertEquals("32x32 64x64", icons.first().sizes)
        assertEquals("(prefers-color-scheme: dark)", icons.first().media)
    }

    @Test
    fun ignoresMissingAndUnsupportedLinks() {
        assertEquals(emptyList(), websiteIcons("https://example.com", "<link rel=icon><link rel=icon href='javascript:alert(1)'>"))
    }

    @Test
    fun relativeLinkUsesPageDirectoryAndFirstBase() {
        assertEquals(
            "https://example.com/docs/icon.ico",
            websiteIcons("https://example.com/docs/page", "<link rel=icon href=icon.ico>").single().url,
        )
        assertEquals(
            "https://example.com/a/icon.ico",
            websiteIcons("https://example.com/docs/page", "<base href=/a/><base href=/b/><link rel=icon href=icon.ico>").single().url,
        )
    }
}
