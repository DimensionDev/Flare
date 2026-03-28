package dev.dimension.flare.ui.render

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TranslationJsonUiRichTextTest {
    @Test
    fun toTranslationJson_exports_projected_document() {
        val richText =
            parseHtml(
                """<p>Hello <strong>world</strong> @alice https://example.com #topic <emoji target="https://example.com/e.png" alt=":wave:"/></p>""",
            ).toUi()

        assertEquals(
            """{"version":1,"targetLanguage":"zh-CN","blocks":[{"id":0,"tokens":[{"id":0,"kind":"Translatable","text":"Hello "},{"id":1,"kind":"Translatable","text":"world"},{"id":2,"kind":"Locked","text":" @alice https://example.com #topic "}]}]}""",
            richText.toTranslationJson("zh-CN"),
        )
    }

    @Test
    fun applyTranslationJson_replaces_text_while_preserving_styles_and_images() {
        val richText =
            parseHtml(
                "<p>Hello <strong>world</strong><emoji target=\"https://example.com/e.png\" alt=\":wave:\"/> from Tokyo</p><blockquote>Original quote</blockquote>",
            ).toUi()

        val translated =
            richText.applyTranslationJson(
                """{"version":1,"targetLanguage":"zh-CN","blocks":[{"id":0,"tokens":[{"id":0,"kind":"Translatable","text":"你好 "},{"id":1,"kind":"Translatable","text":"世界"},{"id":2,"kind":"Translatable","text":" 来自东京"}]},{"id":1,"tokens":[{"id":0,"kind":"Translatable","text":"翻译后的引用"}]}]}""",
            )

        assertEquals("你好 世界:wave: 来自东京翻译后的引用", translated.raw)
        assertEquals(2, translated.renderRuns.size)

        val first = assertIs<RenderContent.Text>(translated.renderRuns[0])
        assertEquals(4, first.runs.size)
        assertEquals("你好 ", assertIs<RenderRun.Text>(first.runs[0]).text)
        val bold = assertIs<RenderRun.Text>(first.runs[1])
        assertEquals("世界", bold.text)
        assertTrue(bold.style.bold)
        val emoji = assertIs<RenderRun.Image>(first.runs[2])
        assertEquals(":wave:", emoji.alt)
        assertEquals(" 来自东京", assertIs<RenderRun.Text>(first.runs[3]).text)

        val quote = assertIs<RenderContent.Text>(translated.renderRuns[1])
        assertTrue(quote.block.isBlockQuote)
        assertEquals("翻译后的引用", assertIs<RenderRun.Text>(quote.runs.single()).text)
    }

    @Test
    fun applyTranslationJson_rejects_modified_locked_tokens() {
        val richText = "Hello @alice".toUiPlainText()

        assertFailsWith<TranslationFormatException> {
            richText.applyTranslationJson(
                """{"version":1,"blocks":[{"id":0,"tokens":[{"id":0,"kind":"Translatable","text":"你好 "},{"id":1,"kind":"Locked","text":"@bob"}]}]}""",
            )
        }
    }
}
