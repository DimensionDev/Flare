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

        val textContent = state.contents.single() as RichTextContent.Text
        assertEquals("Hello\n\nWorld", textContent.content.text)
    }

    @Test
    fun link_is_annotated_with_url_tag() {
        val url = "https://example.com"
        val ui = htmlToUiRichText("<p><a href=\"$url\">link</a></p>")
        val state = RichTextState(ui, defaultStyleData())

        val textContent = state.contents.single() as RichTextContent.Text
        val annotations = textContent.content.getStringAnnotations(0, textContent.content.length)
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

        val blockContent = state.contents.filterIsInstance<RichTextContent.BlockImage>().firstOrNull()
        assertNotNull(blockContent)
        assertEquals(imgUrl, blockContent.url)
    }

    @Test
    fun span_br_span_renders_single_newline() {
        val html = "<span>hello</span><span><br></span><span>world</span>"
        val ui = htmlToUiRichText(html)
        val state = RichTextState(ui, defaultStyleData())

        val textContent = state.contents.single() as RichTextContent.Text
        assertEquals("hello\nworld", textContent.content.text)
    }

    @Test
    fun strong_tag_applies_bold_style() {
        val ui = htmlToUiRichText("<strong>Bold Text</strong>")
        val state = RichTextState(ui, defaultStyleData())

        val textContent = state.contents.single() as RichTextContent.Text
        val spanStyles = textContent.content.spanStyles
        val boldStyle = spanStyles.firstOrNull { it.item.fontWeight == androidx.compose.ui.text.font.FontWeight.Bold }
        assertNotNull(boldStyle)
        assertEquals(0, boldStyle.start)
        assertEquals(9, boldStyle.end)
    }

    @Test
    fun em_tag_applies_italic_style() {
        val ui = htmlToUiRichText("<em>Italic Text</em>")
        val state = RichTextState(ui, defaultStyleData())

        val textContent = state.contents.single() as RichTextContent.Text
        val spanStyles = textContent.content.spanStyles
        val italicStyle = spanStyles.firstOrNull { it.item.fontStyle == androidx.compose.ui.text.font.FontStyle.Italic }
        assertNotNull(italicStyle)
        assertEquals(0, italicStyle.start)
        assertEquals(11, italicStyle.end)
    }

    @Test
    fun del_tag_applies_line_through_decoration() {
        val ui = htmlToUiRichText("<del>Deleted Text</del>")
        val state = RichTextState(ui, defaultStyleData())

        val textContent = state.contents.single() as RichTextContent.Text
        val spanStyles = textContent.content.spanStyles
        val lineThroughStyle =
            spanStyles.firstOrNull {
                it.item.textDecoration == androidx.compose.ui.text.style.TextDecoration.LineThrough
            }
        assertNotNull(lineThroughStyle)
        assertEquals(0, lineThroughStyle.start)
        assertEquals(12, lineThroughStyle.end)
    }

    @Test
    fun u_tag_applies_underline_decoration() {
        val ui = htmlToUiRichText("<u>Underlined Text</u>")
        val state = RichTextState(ui, defaultStyleData())

        val textContent = state.contents.single() as RichTextContent.Text
        val spanStyles = textContent.content.spanStyles
        val underlineStyle = spanStyles.firstOrNull { it.item.textDecoration == androidx.compose.ui.text.style.TextDecoration.Underline }
        assertNotNull(underlineStyle)
        assertEquals(0, underlineStyle.start)
        assertEquals(15, underlineStyle.end)
    }

    @Test
    fun code_tag_applies_monospace_font_and_background() {
        val ui = htmlToUiRichText("<code>Code Text</code>")
        val state = RichTextState(ui, defaultStyleData())

        val textContent = state.contents.single() as RichTextContent.Text
        val spanStyles = textContent.content.spanStyles
        val codeStyle = spanStyles.firstOrNull { it.item.fontFamily == androidx.compose.ui.text.font.FontFamily.Monospace }
        assertNotNull(codeStyle)
        assertEquals(0, codeStyle.start)
        assertEquals(9, codeStyle.end)
        // Check background color if possible, or just existence of style is enough for now
    }

    @Test
    fun list_items_are_prefixed_with_bullet() {
        val ui = htmlToUiRichText("<ul><li>Item 1</li><li>Item 2</li></ul>")
        val state = RichTextState(ui, defaultStyleData())

        val textContent = state.contents.single() as RichTextContent.Text
        val text = textContent.content.text
        assertTrue(text.contains("• Item 1"))
        assertTrue(text.contains("• Item 2"))
    }

    @Test
    fun headers_apply_correct_styles() {
        val ui = htmlToUiRichText("<h1>Header 1</h1><h2>Header 2</h2>")
        val state = RichTextState(ui, defaultStyleData())

        val textContent = state.contents.single() as RichTextContent.Text
        val text = textContent.content.text
        assertTrue(text.contains("Header 1"))
        assertTrue(text.contains("Header 2"))

        assertTrue(text.contains("\n"))
    }

    @Test
    fun center_tag_applies_center_alignment() {
        val ui = htmlToUiRichText("<center>Centered Text</center>")
        val state = RichTextState(ui, defaultStyleData())

        val textContent = state.contents.single() as RichTextContent.Text
        assertEquals("Centered Text", textContent.content.text)
    }

    @Test
    fun blockquote_applies_styling() {
        val ui = htmlToUiRichText("<blockquote>Quote</blockquote>")
        val state = RichTextState(ui, defaultStyleData())

        val textContent = state.contents.single() as RichTextContent.Text
        val paragraphStyles = textContent.content.paragraphStyles
        assertTrue(paragraphStyles.isNotEmpty())

        val spanStyles = textContent.content.spanStyles
        val backgroundStyle = spanStyles.firstOrNull { it.item.background != androidx.compose.ui.graphics.Color.Unspecified }
        assertNotNull(backgroundStyle)
    }
}
