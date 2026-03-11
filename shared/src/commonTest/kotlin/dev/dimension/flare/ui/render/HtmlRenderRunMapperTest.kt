package dev.dimension.flare.ui.render

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HtmlRenderRunMapperTest {
    @Test
    fun maps_inline_styles_into_single_text_block() {
        val contents = map("<p>Hello <strong><a href=\"https://example.com\">world</a></strong><small><u>!</u></small></p>")

        val text = assertIs<RenderContent.Text>(contents.single())
        assertEquals(RenderBlockStyle(), text.block)
        assertEquals(3, text.runs.size)

        assertEquals("Hello ", assertIs<RenderRun.Text>(text.runs[0]).text)

        val linked = assertIs<RenderRun.Text>(text.runs[1])
        assertEquals("world", linked.text)
        assertTrue(linked.style.bold)
        assertEquals("https://example.com", linked.style.link)

        val smallUnderlined = assertIs<RenderRun.Text>(text.runs[2])
        assertEquals("!", smallUnderlined.text)
        assertTrue(smallUnderlined.style.small)
        assertTrue(smallUnderlined.style.underline)
    }

    @Test
    fun maps_distinct_blocks_without_synthetic_newline_runs() {
        val contents = map("<h1>Title</h1><p>Body</p><center>Centered</center>")

        assertEquals(3, contents.size)

        val heading = assertIs<RenderContent.Text>(contents[0])
        assertEquals(1, heading.block.headingLevel)
        assertEquals("Title", assertIs<RenderRun.Text>(heading.runs.single()).text)

        val paragraph = assertIs<RenderContent.Text>(contents[1])
        assertNull(paragraph.block.headingLevel)
        assertEquals("Body", assertIs<RenderRun.Text>(paragraph.runs.single()).text)

        val centered = assertIs<RenderContent.Text>(contents[2])
        assertEquals(RenderTextAlignment.Center, centered.block.textAlignment)
        assertEquals("Centered", assertIs<RenderRun.Text>(centered.runs.single()).text)
    }

    @Test
    fun maps_list_items_as_independent_blocks() {
        val contents = map("<ul><li>One</li><li><strong>Two</strong></li></ul>")

        assertEquals(2, contents.size)

        val first = assertIs<RenderContent.Text>(contents[0])
        assertTrue(first.block.isListItem)
        assertEquals("\u2022 One", assertIs<RenderRun.Text>(first.runs.single()).text)

        val second = assertIs<RenderContent.Text>(contents[1])
        assertTrue(second.block.isListItem)
        assertEquals(2, second.runs.size)
        assertEquals("\u2022 ", assertIs<RenderRun.Text>(second.runs[0]).text)
        val bold = assertIs<RenderRun.Text>(second.runs[1])
        assertEquals("Two", bold.text)
        assertTrue(bold.style.bold)
    }

    @Test
    fun maps_figure_content_into_block_image_and_figcaption() {
        val contents =
            map(
                "<figure><img src=\"https://example.com/image.png\" href=\"https://example.com/post\"/><figcaption>Caption</figcaption></figure>",
            )

        assertEquals(2, contents.size)

        val image = assertIs<RenderContent.BlockImage>(contents[0])
        assertEquals("https://example.com/image.png", image.url)
        assertEquals("https://example.com/post", image.href)

        val caption = assertIs<RenderContent.Text>(contents[1])
        assertTrue(caption.block.isFigCaption)
        assertEquals(RenderTextAlignment.Center, caption.block.textAlignment)
        val run = assertIs<RenderRun.Text>(caption.runs.single())
        assertEquals("Caption", run.text)
        assertTrue(run.style.italic)
        assertTrue(run.style.small)
    }

    @Test
    fun maps_inline_media_and_time_without_losing_surrounding_text() {
        val contents =
            map(
                "<p>Hi <emoji target=\"https://example.com/e.png\" alt=\":wave:\"/> <time>now</time> <img src=\"https://example.com/i.png\" alt=\"[img]\"/></p>",
            )

        val text = assertIs<RenderContent.Text>(contents.single())
        assertEquals(6, text.runs.size)

        assertEquals("Hi ", assertIs<RenderRun.Text>(text.runs[0]).text)

        val emoji = assertIs<RenderRun.Image>(text.runs[1])
        assertEquals("https://example.com/e.png", emoji.url)
        assertEquals(":wave:", emoji.alt)

        assertEquals(" ", assertIs<RenderRun.Text>(text.runs[2]).text)

        val time = assertIs<RenderRun.Text>(text.runs[3])
        assertEquals("now", time.text)
        assertTrue(time.style.time)

        assertEquals(" ", assertIs<RenderRun.Text>(text.runs[4]).text)

        val inlineImage = assertIs<RenderRun.Image>(text.runs[5])
        assertEquals("https://example.com/i.png", inlineImage.url)
        assertEquals("[img]", inlineImage.alt)
    }

    @Test
    fun maps_blockquote_and_code_styles_independently() {
        val contents = map("<blockquote><code>quoted</code></blockquote>")

        val text = assertIs<RenderContent.Text>(contents.single())
        assertTrue(text.block.isBlockQuote)
        val run = assertIs<RenderRun.Text>(text.runs.single())
        assertEquals("quoted", run.text)
        assertTrue(run.style.code)
        assertTrue(run.style.monospace)
    }

    private fun map(html: String) = mapHtmlToRenderContents(parseHtml(html))
}
