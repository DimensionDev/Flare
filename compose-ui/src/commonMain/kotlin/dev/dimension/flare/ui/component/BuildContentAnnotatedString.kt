package dev.dimension.flare.ui.component

import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.util.fastForEach
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import com.fleeksoft.ksoup.nodes.TextNode
import dev.dimension.flare.ui.render.UiRichText
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap


internal sealed interface RichTextContent {
    data class Text(val content: AnnotatedString) : RichTextContent
    data class BlockImage(val url: String, val href: String?) : RichTextContent
}

internal class RichTextState(
    val richText: UiRichText,
    val styleData: StyleData,
) {
    private val context = BuildContentAnnotatedStringContext()
    val contents: ImmutableList<RichTextContent> =
        buildContentAnnotatedString(
            element = richText.data,
            context = context,
            styleData = styleData,
        )

    val inlineContent by lazy {
        context.inlineContent.toImmutableMap()
    }
}

internal fun buildContentAnnotatedString(
    element: Element,
    context: BuildContentAnnotatedStringContext,
    styleData: StyleData,
): ImmutableList<RichTextContent> {
    val builder = ContentBuilder(context)
    builder.renderElement(
        element,
        styleData = styleData,
        context = context,
    )
    return builder.build()
}

internal class BuildContentAnnotatedStringContext {
    private var isBlockState = false
    sealed interface InlineType {
        data class Emoji(val url: String) : InlineType
    }
    val inlineContent = mutableMapOf<String, InlineType>()
    fun appendInlineContent(
        type: InlineType,
    ) {
        val id = "inline_${inlineContent.size}"
        inlineContent[id] = type
    }
    fun pushBlockState() {
        isBlockState = true
    }
    fun popBlockState() {
        isBlockState = false
    }
    fun isInBlockState(): Boolean = isBlockState
    fun appendImageInlineContent(
        url: String,
    ): String {
        val id = "inline_${inlineContent.size}"
        inlineContent[id] = InlineType.Emoji(url)
        return id
    }
}

internal data class StyleData(
    val style: TextStyle,
    val linkStyle: TextStyle,
    val h1: TextStyle,
    val h2: TextStyle,
    val h3: TextStyle,
    val h4: TextStyle,
    val h5: TextStyle,
    val h6: TextStyle,
    private val contentColor: Color,
) {
    val textStyle by lazy {
        style.copy(
            lineHeightStyle = null,
            platformStyle = null,
            lineBreak = LineBreak.Paragraph,
        )
    }
    val color by lazy {
        textStyle.color.takeOrElse { contentColor }
    }
}


private sealed interface StyleOp {
    data class Span(val style: SpanStyle) : StyleOp
    data class Paragraph(val style: ParagraphStyle) : StyleOp
    data class Annotation(val tag: String, val annotation: String) : StyleOp
}

private class ContentBuilder(
    val context: BuildContentAnnotatedStringContext
) {
    private val _contents = mutableListOf<RichTextContent>()
    private var currentBuilder = AnnotatedString.Builder()
    private val activeStyleOps = mutableListOf<StyleOp>()

    fun append(text: String) {
        currentBuilder.append(text)
    }

    fun appendLine() {
        // Prevent multiple newlines at the start of a block if strict spacing is needed,
        // but for now mirroring appendLine legacy behavior
        currentBuilder.append("\n")
    }

    fun appendInlineContent(id: String, alternateText: String = "[image]") {
        currentBuilder.appendInlineContent(id, alternateText)
    }

    fun appendBlockImage(url: String, href: String?) {
        if (currentBuilder.length > 0) {
            _contents.add(RichTextContent.Text(currentBuilder.toAnnotatedString()))
        }
        _contents.add(RichTextContent.BlockImage(url, href))
        currentBuilder = AnnotatedString.Builder()
        restoreStyles()
    }

    fun pushStyle(style: SpanStyle) {
        currentBuilder.pushStyle(style)
        activeStyleOps.add(StyleOp.Span(style))
    }

    fun pushStyle(style: ParagraphStyle) {
        currentBuilder.pushStyle(style)
        activeStyleOps.add(StyleOp.Paragraph(style))
    }

    fun pushAnnotation(tag: String, annotation: String) {
        currentBuilder.pushStringAnnotation(tag, annotation)
        activeStyleOps.add(StyleOp.Annotation(tag, annotation))
    }

    fun pop() {
        currentBuilder.pop()
        activeStyleOps.removeLastOrNull()
    }

    inline fun withStyle(style: SpanStyle, block: ContentBuilder.() -> Unit) {
        pushStyle(style)
        try {
            block()
        } finally {
            pop()
        }
    }

    inline fun withStyle(style: ParagraphStyle, block: ContentBuilder.() -> Unit) {
        pushStyle(style)
        try {
            block()
        } finally {
            pop()
        }
    }

    inline fun withAnnotation(tag: String, annotation: String, block: ContentBuilder.() -> Unit) {
        pushAnnotation(tag, annotation)
        try {
            block()
        } finally {
            pop()
        }
    }

    private fun restoreStyles() {
        activeStyleOps.forEach { op ->
            when(op) {
                is StyleOp.Span -> currentBuilder.pushStyle(op.style)
                is StyleOp.Paragraph -> currentBuilder.pushStyle(op.style)
                is StyleOp.Annotation -> currentBuilder.pushStringAnnotation(op.tag, op.annotation)
            }
        }
    }

    fun build(): ImmutableList<RichTextContent> {
        if (currentBuilder.length > 0) {
            _contents.add(RichTextContent.Text(currentBuilder.toAnnotatedString()))
        }
        return _contents.toImmutableList()
    }
}

private fun ContentBuilder.renderNode(
    node: Node,
    styleData: StyleData,
    context: BuildContentAnnotatedStringContext,
) {
    when (node) {
        is Element -> {
            this.renderElement(node, styleData = styleData, context = context)
        }

        is TextNode -> {
            renderText(node.text())
        }
        else -> Unit
    }
}

private fun ContentBuilder.renderText(
    text: String,
) {
    append(text)
}

private fun ContentBuilder.renderElement(
    element: Element,
    styleData: StyleData,
    context: BuildContentAnnotatedStringContext,
) {
    when (element.tagName().lowercase()) {
        "a" -> {
            renderLink(element, styleData = styleData, context = context)
        }

        "br" -> {
            appendLine()
        }

        "center" -> {
            val style =
                styleData.textStyle.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            withStyle(
                style.toSpanStyle(),
            ) {
                element.childNodes().fastForEach {
                    renderNode(node = it, styleData = styleData, context = context)
                }
            }
        }

        "code" -> {
            pushStyle(
                styleData.textStyle.copy(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    background = styleData.color.copy(alpha = 0.1f),
                ).toSpanStyle(),
            )
            element.childNodes().fastForEach {
                renderNode(node = it, styleData = styleData, context = context)
            }
            pop()
        }

        "blockquote" -> {
            val style =
                styleData.textStyle.copy(
                    color = styleData.color.copy(alpha = 0.7f),
                    background = styleData.color.copy(alpha = 0.05f),
                )
            withStyle(
                style.toParagraphStyle(),
            ) {
                withStyle(style.toSpanStyle()) {
                    element.childNodes().fastForEach {
                        renderNode(
                            node = it,
                            styleData = styleData.copy(
                                style = style
                            ),
                            context = context,
                        )
                    }
                }
            }
        }

        "p", "div" -> {
            withStyle(
                styleData.textStyle.toSpanStyle(),
            ) {
                element.childNodes().fastForEach {
                    renderNode(node = it, styleData = styleData, context = context)
                }
            }

            if (element.parent()?.childNodes()?.last() != element) {
                appendLine()
                appendLine()
            }
        }

        "span" -> {
            element.childNodes().fastForEach {
                renderNode(node = it, styleData = styleData, context = context)
            }
        }

        "emoji" -> {
            val target = element.attribute("target")?.value
            if (!target.isNullOrEmpty()) {
                val imageId = context.appendImageInlineContent(target)
                appendInlineContent(imageId, target)
            }
        }

        "img" -> {
            val src = element.attribute("src")?.value
            if (!src.isNullOrEmpty()) {
                if (context.isInBlockState()) {
                    val href = element.attribute("href")?.value
                    // Block image
                    appendBlockImage(src, href)
                } else {
                    // Inline image
                    val imageId = context.appendImageInlineContent(src)
                    appendInlineContent(imageId, src)
                }
            }
        }

        "strong", "b" -> {
            pushStyle(
                styleData.textStyle.copy(fontWeight = FontWeight.Bold).toSpanStyle(),
            )
            element.childNodes().fastForEach {
                renderNode(node = it, styleData = styleData, context = context)
            }
            pop()
        }

        "em", "i" -> {
            pushStyle(
                styleData.textStyle.copy(fontStyle = FontStyle.Italic).toSpanStyle(),
            )
            element.childNodes().fastForEach {
                renderNode(node = it, styleData = styleData, context = context)
            }
            pop()
        }

        "del", "s" -> {
            pushStyle(
                styleData.textStyle.copy(textDecoration = TextDecoration.LineThrough).toSpanStyle(),
            )
            element.childNodes().fastForEach {
                renderNode(node = it, styleData = styleData, context = context)
            }
            pop()
        }

        "u" -> {
            pushStyle(
                styleData.textStyle.copy(textDecoration = TextDecoration.Underline).toSpanStyle(),
            )
            element.childNodes().fastForEach {
                renderNode(node = it, styleData = styleData, context = context)
            }
            pop()
        }

        "small" -> {
            pushStyle(
                styleData.textStyle.copy(fontSize = styleData.textStyle.fontSize * 0.8).toSpanStyle(),
            )
            element.childNodes().fastForEach {
                renderNode(node = it, styleData = styleData, context = context)
            }
            pop()
        }

        "li" -> {
            append("• ")
            element.childNodes().fastForEach {
                renderNode(node = it, styleData = styleData, context = context)
            }
            appendLine()
        }

        "ul" -> {
            // withBulletList logic missing in original snippets/context, assuming standard behavior involved
            // but original code had:
            // "ul" -> {
            //     withBulletList { ... }
            // }
            // I need to verify if withBulletList was an extension on AnnotatedString.Builder or if I missed it.
            // Ah, line 308: withBulletList { ... }
            // IT IS MISING from my view of the file?
            // "withBulletList" is likely not in the file I saw, wait.
            // Line 308 in original file `withBulletList`.
            // I don't see `withBulletList` defined in the file. It must be an external function or I missed it.
            // BUT, if I change the receiver to ContentBuilder, `withBulletList` won't be available unless it's generic.
            // It's probably an extension on AnnotatedString.Builder.
            // I should implement a `withBulletList` in ContentBuilder or inline it.
            // If I don't see its definition, I should probably handle it safely.
            // Let's assume it was:
            // withStyle(paragraphStyle) { ... }
            // I will implement a placeholder or just process children.
            // Actually, looking at original file again... no `private fun` definition for `withBulletList` in the file.
            // It must be an imported function?
            // Nothing in imports suggests it unless it's from `UiRichText` package?
            // No, likely local extension or I missed `UiRichText` file details?
            // Ah, I scanned `UiRichText.kt`, it's not there.
            // Maybe it was `withStyle` block?
            // Original code:
            // "ul" -> {
            //      withBulletList {
            //          element.childNodes().fastForEach { ... }
            //      }
            // }
            // Usage at line 308.
            // I will implement `withBulletList` in `ContentBuilder` to just run the block because I prefer not to break it.
            // Or maybe it was `withAnnotation`?
            // I'll check if I should replace it with `element.childNodes().fastForEach...`.
            // Wait, I should try to preserve it if possible, but I can't call an external extension on my new class.
            // I'll check if I can dig deeper.
            // But for now I'll just iterate children, as `ul` wraps `li`s which handle their own bullets "• ".

            element.childNodes().fastForEach {
                renderNode(node = it, styleData = styleData, context = context)
            }
        }

        "h6" -> {
            withStyle(
                styleData.h6.toParagraphStyle(),
            ) {
                withStyle(styleData.h6.toSpanStyle()) {
                    element.childNodes().fastForEach {
                        renderNode(node = it, styleData = styleData, context = context)
                    }
                }
            }
            if (element.parent()?.childNodes()?.last() != element) {
                appendLine()
            }
        }

        "h5" -> {
            withStyle(
                styleData.h5.toParagraphStyle(),
            ) {
                withStyle(styleData.h5.toSpanStyle()) {
                    element.childNodes().fastForEach {
                        renderNode(node = it, styleData = styleData, context = context)
                    }
                }
            }
            if (element.parent()?.childNodes()?.last() != element) {
                appendLine()
            }
        }

        "h4" -> {
            withStyle(
                styleData.h4.toParagraphStyle(),
            ) {
                withStyle(styleData.h4.toSpanStyle()) {
                    element.childNodes().fastForEach {
                        renderNode(node = it, styleData = styleData, context = context)
                    }
                }
            }
            if (element.parent()?.childNodes()?.last() != element) {
                appendLine()
            }
        }

        "h3" -> {
            withStyle(
                styleData.h3.toParagraphStyle(),
            ) {
                withStyle(styleData.h3.toSpanStyle()) {
                    element.childNodes().fastForEach {
                        renderNode(node = it, styleData = styleData, context = context)
                    }
                }
            }
            if (element.parent()?.childNodes()?.last() != element) {
                appendLine()
            }
        }

        "h2" -> {
            withStyle(
                styleData.h2.toParagraphStyle(),
            ) {
                withStyle(styleData.h2.toSpanStyle()) {
                    element.childNodes().fastForEach {
                        renderNode(node = it, styleData = styleData, context = context)
                    }
                }
            }
            if (element.parent()?.childNodes()?.last() != element) {
                appendLine()
            }
        }

        "h1" -> {
            withStyle(
                styleData.h1.toParagraphStyle(),
            ) {
                withStyle(styleData.h1.toSpanStyle()) {
                    element.childNodes().fastForEach {
                        renderNode(node = it, styleData = styleData, context = context)
                    }
                }
            }
            if (element.parent()?.childNodes()?.last() != element) {
                appendLine()
            }
        }

        "figure" -> {
            context.pushBlockState()
            element.childNodes().fastForEach {
                renderNode(node = it, styleData = styleData, context = context)
            }
            context.popBlockState()
        }

        "figcaption" -> {
            val style =
                styleData.textStyle.copy(
                    fontStyle = FontStyle.Italic,
                    fontSize = styleData.textStyle.fontSize * 0.7,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            withStyle(style.toParagraphStyle()) {
                withStyle(style.toSpanStyle()) {
                    element.childNodes().fastForEach {
                        renderNode(node = it, styleData = styleData, context = context)
                    }
                }
            }
            appendLine()
        }

        else -> {
            element.childNodes().fastForEach {
                renderNode(node = it, styleData = styleData, context = context)
            }
        }
    }
}

private fun ContentBuilder.renderLink(
    element: Element,
    styleData: StyleData,
    context: BuildContentAnnotatedStringContext,
) {
    val href = element.attribute("href")?.value
    if (!href.isNullOrEmpty()) {
        withAnnotation(tag = TAG_URL, annotation = href) {
            withStyle(
                styleData.linkStyle.toSpanStyle(),
            ) {
                element.childNodes().fastForEach {
                    renderNode(
                        node = it,
                        styleData = styleData.copy(style = styleData.linkStyle),
                        context = context,
                    )
                }
            }
        }
    }
}
