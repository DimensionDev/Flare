package dev.dimension.flare.ui.render

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HtmlRenderRunMapperTest {
    @Test
    fun resolves_relative_links_against_the_source_page() {
        val links =
            mapOf(
                "/posts/123" to "https://example.com/posts/123",
                "../next" to "https://example.com/next",
                "//other.example/post" to "https://other.example/post",
                "#section" to "https://example.com/articles/current#section",
            )
        links.forEach { (href, expected) ->
            val element = parseHtml("""<a href="$href">link</a>""", baseUri = "https://example.com/articles/current")
            val content = assertIs<RenderContent.Text>(mapHtmlToRenderContents(element).single())
            assertEquals(expected, assertIs<RenderRun.Text>(content.runs.single()).style.link)
        }
    }

    @Test
    fun does_not_make_blank_or_unresolved_links_clickable() {
        listOf("", " ", "#section", "/posts/123", "//example.com/post", "example.com/post").forEach { href ->
            val content = assertIs<RenderContent.Text>(map("""<a href="$href">link</a>""").single())
            val run = assertIs<RenderRun.Text>(content.runs.single())
            assertEquals("link", run.text)
            assertNull(run.style.link, href)
        }
    }

    @Test
    fun preserves_absolute_and_internal_links() {
        listOf("https://example.com/post", "flare://Settings", "mailto:hello@example.com", "tel:123").forEach { href ->
            val content = assertIs<RenderContent.Text>(map("""<a href=" $href ">link</a>""").single())
            assertEquals(href, assertIs<RenderRun.Text>(content.runs.single()).style.link)
        }
    }

    @Test
    fun resolves_block_image_links_against_the_source_page() {
        val element =
            parseHtml(
                """<figure><img src="https://example.com/image.jpg" href="/posts/123"/></figure>""",
                baseUri = "https://example.com/article",
            )
        val image = assertIs<RenderContent.BlockImage>(mapHtmlToRenderContents(element).single())
        assertEquals("https://example.com/posts/123", image.href)
    }

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
    fun ignores_formatting_whitespace_between_blocks() {
        val contents =
            map(
                """
                <p>Hello</p>
                <p>World</p>
                """.trimIndent(),
            )

        assertEquals(2, contents.size)
        val first = assertIs<RenderContent.Text>(contents[0])
        assertEquals("Hello", assertIs<RenderRun.Text>(first.runs.single()).text)
        val second = assertIs<RenderContent.Text>(contents[1])
        assertEquals("World", assertIs<RenderRun.Text>(second.runs.single()).text)
    }

    @Test
    fun ignores_blank_only_blocks() {
        val contents = map("<p>Hello</p><p><br></p><p>World</p>")

        assertEquals(2, contents.size)
        val first = assertIs<RenderContent.Text>(contents[0])
        assertEquals("Hello", assertIs<RenderRun.Text>(first.runs.single()).text)
        val second = assertIs<RenderContent.Text>(contents[1])
        assertEquals("World", assertIs<RenderRun.Text>(second.runs.single()).text)
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
    fun skips_invisible_span_and_adds_ellipsis_after_ellipsis_span() {
        val contents =
            map(
                """<p><a href="https://peertube.heise.de/w/wCYxw5CA8SAba92qsSbAqo"><span class="invisible">https://</span><span class="ellipsis">peertube.heise.de/w/wCYxw5CA8S</span><span class="invisible">Aba92qsSbAqo</span></a></p>""",
            )

        val text = assertIs<RenderContent.Text>(contents.single())
        val run = assertIs<RenderRun.Text>(text.runs.single())
        assertEquals("peertube.heise.de/w/wCYxw5CA8S…", run.text)
        assertEquals("https://peertube.heise.de/w/wCYxw5CA8SAba92qsSbAqo", run.style.link)
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
