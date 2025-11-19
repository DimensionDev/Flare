package dev.dimension.flare.ui.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.fleeksoft.ksoup.Ksoup
import dev.dimension.flare.ui.render.UiRichText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RichTextStateTest {
    private fun defaultStyleData(): StyleData =
        StyleData(
            style = TextStyle(),
            linkStyle = TextStyle(),
            h1 = TextStyle(),
            h2 = TextStyle(),
            h3 = TextStyle(),
            h4 = TextStyle(),
            h5 = TextStyle(),
            h6 = TextStyle(),
            contentColor = Color.Black,
        )

    private fun htmlToUiRichText(html: String): UiRichText {
        val element = Ksoup.parse(html).body()
        return UiRichText(data = element, isRtl = false)
    }

    @Test
    fun annotatedString_contains_paragraph_text_and_newlines() {
        val ui = htmlToUiRichText("<p>Hello</p><p>World</p>")
        val state = RichTextState(ui, defaultStyleData())

        assertEquals("Hello\n\nWorld", state.annotatedString.text)
    }

    @Test
    fun link_is_annotated_with_url_tag() {
        val url = "https://example.com"
        val ui = htmlToUiRichText("<p><a href=\"$url\">link</a></p>")
        val state = RichTextState(ui, defaultStyleData())

        val annotations = state.annotatedString.getStringAnnotations(0, state.annotatedString.length)
        val a = annotations.firstOrNull { it.tag == TAG_URL }
        assertNotNull(a)
        assertEquals(url, a.item)
    }

    @Test
    fun emoji_creates_inline_content_entry() {
        val emojiUrl = "https://example.com/emoji.png"
        val ui = htmlToUiRichText("<p>Hi <emoji target=\"$emojiUrl\"/></p>")
        val state = RichTextState(ui, defaultStyleData())

        assertTrue(state.inlineContent.isNotEmpty())
        val anyEmoji = state.inlineContent.values.any { it is BuildContentAnnotatedStringContext.InlineType.Emoji && it.url == emojiUrl }
        assertTrue(anyEmoji, "Expected an Emoji inline content with the provided URL")
    }

    @Test
    fun figure_image_is_marked_as_block_image() {
        val imgUrl = "https://example.com/image.png"
        val ui = htmlToUiRichText("<figure><img src=\"$imgUrl\"/></figure>")
        val state = RichTextState(ui, defaultStyleData())

        assertTrue(state.hasBlockImage)
        val anyBlock = state.inlineContent.values.any { it is BuildContentAnnotatedStringContext.InlineType.BlockImage && it.url == imgUrl }
        assertTrue(anyBlock, "Expected a BlockImage inline content with the provided URL")
    }

    @Test
    fun span_br_span_renders_single_newline() {
        val html = "<span>hello</span><span><br></span><span>world</span>"
        val ui = htmlToUiRichText(html)
        val state = RichTextState(ui, defaultStyleData())

        assertEquals("hello\nworld", state.annotatedString.text)
    }
}
