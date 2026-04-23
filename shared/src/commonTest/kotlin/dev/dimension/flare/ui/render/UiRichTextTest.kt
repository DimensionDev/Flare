package dev.dimension.flare.ui.render

import com.fleeksoft.ksoup.nodes.Element
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UiRichTextTest {
    @Test
    fun serializerDoesNotPrettyPrintBrLines() {
        val uiRichText =
            Element("body")
                .apply {
                    appendText("Line 1")
                    appendChild(Element("br"))
                    appendText("Line 2")
                }.toUi()

        val encoded = Json.encodeToString(uiRichText)
        val decoded = Json.decodeFromString<UiRichText>(encoded)

        assertFalse(encoded.contains("\\n "))
        assertEquals("Line 1\nLine 2", decoded.raw)
    }

    @Test
    fun renderRuns_flattens_styled_text_and_images() {
        val decoded =
            parseHtml(
                """<p>Hello <strong><a href="https://example.com">world</a></strong><emoji target="https://example.com/e.png" alt=":wave:"/></p>""",
            ).toUi()

        val content = assertIs<RenderContent.Text>(decoded.renderRuns.single())
        val runs = content.runs

        assertEquals(3, runs.size)
        assertEquals(RenderBlockStyle(), content.block)

        assertEquals("Hello ", assertIs<RenderRun.Text>(runs[0]).text)

        val linked = assertIs<RenderRun.Text>(runs[1])
        assertEquals("world", linked.text)
        assertTrue(linked.style.bold)
        assertEquals("https://example.com", linked.style.link)

        val image = assertIs<RenderRun.Image>(runs[2])
        assertEquals("https://example.com/e.png", image.url)
        assertEquals(":wave:", image.alt)
    }

    @Test
    fun renderRuns_emits_block_images_as_separate_content() {
        val decoded =
            parseHtml(
                """<p>before</p><figure><img src="https://example.com/image.png" href="https://example.com/post"/></figure>""",
            ).toUi()

        assertEquals(2, decoded.renderRuns.size)

        val text = assertIs<RenderContent.Text>(decoded.renderRuns[0])
        assertEquals("before", assertIs<RenderRun.Text>(text.runs.single()).text)

        val blockImage = assertIs<RenderContent.BlockImage>(decoded.renderRuns[1])
        assertEquals("https://example.com/image.png", blockImage.url)
        assertEquals("https://example.com/post", blockImage.href)
    }

    @Test
    fun renderRuns_captures_heading_center_and_list_block_semantics() {
        val decoded = parseHtml("<h2>Header</h2><center>Centered</center><ul><li>Item</li></ul>").toUi()

        assertEquals(3, decoded.renderRuns.size)

        val heading = assertIs<RenderContent.Text>(decoded.renderRuns[0])
        assertEquals(2, heading.block.headingLevel)
        assertEquals("Header", assertIs<RenderRun.Text>(heading.runs.single()).text)

        val centered = assertIs<RenderContent.Text>(decoded.renderRuns[1])
        assertEquals(RenderTextAlignment.Center, centered.block.textAlignment)
        assertEquals("Centered", assertIs<RenderRun.Text>(centered.runs.single()).text)

        val listItem = assertIs<RenderContent.Text>(decoded.renderRuns[2])
        assertTrue(listItem.block.isListItem)
        assertEquals("\u2022 Item", assertIs<RenderRun.Text>(listItem.runs.single()).text)
    }

    @Test
    fun renderRuns_captures_figcaption_time_and_blockquote_semantics() {
        val decoded =
            parseHtml(
                "<blockquote>Quote</blockquote><figure><figcaption>Caption</figcaption></figure><p><time>now</time></p>",
            ).toUi()

        assertEquals(3, decoded.renderRuns.size)

        val quote = assertIs<RenderContent.Text>(decoded.renderRuns[0])
        assertTrue(quote.block.isBlockQuote)
        assertEquals("Quote", assertIs<RenderRun.Text>(quote.runs.single()).text)

        val figcaption = assertIs<RenderContent.Text>(decoded.renderRuns[1])
        assertTrue(figcaption.block.isFigCaption)
        assertEquals(RenderTextAlignment.Center, figcaption.block.textAlignment)
        val captionRun = assertIs<RenderRun.Text>(figcaption.runs.single())
        assertEquals("Caption", captionRun.text)
        assertTrue(captionRun.style.italic)
        assertTrue(captionRun.style.small)

        val time = assertIs<RenderContent.Text>(decoded.renderRuns[2])
        val timeRun = assertIs<RenderRun.Text>(time.runs.single())
        assertEquals("now", timeRun.text)
        assertTrue(timeRun.style.time)
    }

    @Test
    fun toTranslatableText_flattens_runs_into_plain_text() {
        val decoded =
            parseHtml(
                """
                <h2>Hello</h2>
                <p><a href="https://example.com">@friend</a> <strong>world</strong><emoji target="https://example.com/e.png" alt=":wave:"/></p>
                <figure><img src="https://example.com/image.png" href="https://example.com/post"/></figure>
                <figure><figcaption>Caption</figcaption></figure>
                """.trimIndent(),
            ).toUi()

        assertEquals("Hello\n@friend world:wave:\nCaption", decoded.toTranslatableText())
    }

    @Test
    fun toUiPlainText_preserves_literal_text() {
        val decoded = "1 < 2 && @friend".toUiPlainText()

        assertEquals("1 < 2 && @friend", decoded.raw)
        assertEquals("1 < 2 && @friend", decoded.innerText)
        val content = assertIs<RenderContent.Text>(decoded.renderRuns.single())
        assertEquals("1 < 2 && @friend", assertIs<RenderRun.Text>(content.runs.single()).text)
    }

    @Test
    fun rtlDetection_fastPaths_blank_and_latin_text_as_ltr() {
        assertFalse("".toUiPlainText().isRtl)
        assertFalse("Hello cafe 123!".toUiPlainText().isRtl)
        assertFalse("Café déjà vu".toUiPlainText().isRtl)
        assertFalse("こんにちは世界".toUiPlainText().isRtl)
        assertFalse("中文测试 😀".toUiPlainText().isRtl)
    }

    @Test
    fun rtlDetection_onlyFallsBackForStrongRtlCodePoints() {
        assertTrue("مرحبا".toUiPlainText().isRtl)
        assertTrue("שלום".toUiPlainText().isRtl)
    }

    @Test
    fun rtlDetection_prefers_source_languages() {
        assertTrue("Hello".toUiPlainText(listOf("ar")).isRtl)
        assertFalse("مرحبا".toUiPlainText(listOf("en-US")).isRtl)
        assertTrue(parseHtml("<p>Hello</p>").toUi(listOf("he")).isRtl)
    }
}
