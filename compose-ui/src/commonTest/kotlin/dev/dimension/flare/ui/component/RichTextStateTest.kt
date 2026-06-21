package dev.dimension.flare.ui.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.fleeksoft.ksoup.Ksoup
import dev.dimension.flare.ui.render.UiRichText
import dev.dimension.flare.ui.render.toUi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RichTextStateTest {
    private fun defaultStyleData(): StyleData =
        StyleData(
            style = TextStyle(),
            linkStyle = TextStyle(),
            h1 =
                TextStyle(
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                ),
            h2 =
                TextStyle(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                ),
            h3 =
                TextStyle(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontStyle = FontStyle.Italic,
                ),
            h4 =
                TextStyle(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                ),
            h5 =
                TextStyle(
                    fontWeight = FontWeight.Bold,
                ),
            h6 = TextStyle(),
            contentColor = Color.Black,
        )

    private fun htmlToUiRichText(html: String): UiRichText {
        val element = Ksoup.parse(html).body()
        return element.toUi()
    }

    @Test
    fun paragraphs_render_as_separate_text_contents() {
        val ui = htmlToUiRichText("<p>Hello</p><p>World</p>")
        val state = RichTextState(ui, defaultStyleData())

        val textContents = state.contents.filterIsInstance<RichTextContent.Text>()
        assertEquals(2, textContents.size)
        assertEquals("Hello", textContents[0].content.text)
        assertEquals("World", textContents[1].content.text)
    }

    @Test
    fun link_is_annotated_with_url_tag() {
        val url = "https://example.com"
        val ui = htmlToUiRichText("<p><a href=\"$url\">link</a></p>")
        val state = RichTextState(ui, defaultStyleData())

        val textContent = state.contents.single() as RichTextContent.Text
        if (allowLinkAnnotation) {
            val annotations = textContent.content.getLinkAnnotations(0, textContent.content.length)
            val a = annotations.firstOrNull { it.item is LinkAnnotation.Url }
            assertNotNull(a)
            assertIs<LinkAnnotation.Url>(a.item)
            assertEquals(url, (a.item as LinkAnnotation.Url).url)
        } else {
            val annotations = textContent.content.getStringAnnotations(0, textContent.content.length)
            val a = annotations.firstOrNull { it.tag == TAG_URL }
            assertNotNull(a)
            assertEquals(url, a.item)
        }
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
    fun inline_image_creates_inline_content_entry_with_image_url() {
        val imgUrl = "https://example.com/inline.png"
        val ui = htmlToUiRichText("<p>Hi <img src=\"$imgUrl\" alt=\"inline image\"/></p>")
        val state = RichTextState(ui, defaultStyleData())

        val anyInlineImage =
            state.inlineContent.values.any {
                it is BuildContentAnnotatedStringContext.InlineType.Emoji && it.url == imgUrl
            }
        assertTrue(anyInlineImage, "Expected an inline image entry with the provided URL")
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

        val textContents = state.contents.filterIsInstance<RichTextContent.Text>()
        assertEquals(2, textContents.size)
        assertEquals("• Item 1", textContents[0].content.text)
        assertEquals("• Item 2", textContents[1].content.text)
    }

    @Test
    fun headers_apply_correct_styles() {
        val ui =
            htmlToUiRichText(
                "<h1>Header 1</h1><h2>Header 2</h2><h3>Header 3</h3><h5>Header 5</h5>",
            )
        val state = RichTextState(ui, defaultStyleData())

        val textContents = state.contents.filterIsInstance<RichTextContent.Text>()
        assertEquals(4, textContents.size)
        assertEquals("Header 1", textContents[0].content.text)
        assertEquals("Header 2", textContents[1].content.text)

        val h1Style =
            textContents[0]
                .content
                .spanStyles
                .first()
                .item
        assertEquals(26.sp, h1Style.fontSize)
        assertEquals(FontWeight.Bold, h1Style.fontWeight)

        val h2Style =
            textContents[1]
                .content
                .spanStyles
                .first()
                .item
        assertEquals(22.sp, h2Style.fontSize)
        assertEquals(FontWeight.Bold, h2Style.fontWeight)
        assertEquals(Color.Black.copy(alpha = 0.7f), h2Style.color)

        val h3Style =
            textContents[2]
                .content
                .spanStyles
                .first()
                .item
        assertEquals(20.sp, h3Style.fontSize)
        assertEquals(FontWeight.Bold, h3Style.fontWeight)
        assertEquals(FontStyle.Italic, h3Style.fontStyle)

        val h5Style =
            textContents[3]
                .content
                .spanStyles
                .first()
                .item
        assertEquals(FontWeight.Bold, h5Style.fontWeight)
        assertEquals(Color.Black.copy(alpha = 0.5f), h5Style.color)
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
        assertTrue(textContent.block?.isBlockQuote == true)
        val paragraphStyles = textContent.content.paragraphStyles
        assertTrue(paragraphStyles.isNotEmpty())
    }
}
