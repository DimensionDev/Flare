package dev.dimension.flare.ui.component

import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextDecoration
import dev.dimension.flare.ui.render.RenderBlockStyle
import dev.dimension.flare.ui.render.RenderContent
import dev.dimension.flare.ui.render.RenderRun
import dev.dimension.flare.ui.render.RenderTextAlignment
import dev.dimension.flare.ui.render.RenderTextStyle
import dev.dimension.flare.ui.render.UiRichText
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap


internal sealed interface RichTextContent {
    data class Text(
        val content: AnnotatedString,
        val block: RenderBlockStyle? = null,
    ) : RichTextContent
    data class BlockImage(val url: String, val href: String?) : RichTextContent
}

internal class RichTextState(
    val richText: UiRichText,
    val styleData: StyleData,
) {
    private val context = BuildContentAnnotatedStringContext()
    val contents: ImmutableList<RichTextContent> =
        buildContentAnnotatedString(
            renderContents = richText.renderRuns,
            context = context,
            styleData = styleData,
        )

    val inlineContent by lazy {
        context.inlineContent.toImmutableMap()
    }
}

private fun buildContentAnnotatedString(
    renderContents: ImmutableList<RenderContent>,
    context: BuildContentAnnotatedStringContext,
    styleData: StyleData,
): ImmutableList<RichTextContent> {
    val builder = ContentBuilder(context)
    builder.renderContents(renderContents, styleData)
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
        flushText()
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

    inline fun withLinkAnnotation(url: String, block: ContentBuilder.() -> Unit) {
        currentBuilder.pushLink(LinkAnnotation.Url(url))
        try {
            block()
        } finally {
            currentBuilder.pop()
        }
    }

    inline fun withLink(
        url: String,
        block: ContentBuilder.() -> Unit
    ) {
        if (allowLinkAnnotation) {
            withLinkAnnotation(url, block)
        } else {
            withAnnotation(
                tag = TAG_URL,
                annotation = url,
                block = block,
            )
        }
    }

    private fun restoreStyles() {
        activeStyleOps.forEach { op ->
            when (op) {
                is StyleOp.Span -> currentBuilder.pushStyle(op.style)
                is StyleOp.Paragraph -> currentBuilder.pushStyle(op.style)
                is StyleOp.Annotation -> currentBuilder.pushStringAnnotation(op.tag, op.annotation)
            }
        }
    }

    fun flushText(block: RenderBlockStyle? = null) {
        if (currentBuilder.length == 0) return
        _contents.add(RichTextContent.Text(currentBuilder.toAnnotatedString(), block))
        currentBuilder = AnnotatedString.Builder()
        restoreStyles()
    }

    fun build(): ImmutableList<RichTextContent> {
        flushText()
        return _contents.toImmutableList()
    }
}

private fun ContentBuilder.renderContents(
    renderContents: ImmutableList<RenderContent>,
    styleData: StyleData,
) {
    renderContents.forEachIndexed { index, content ->
        when (content) {
            is RenderContent.BlockImage -> appendBlockImage(content.url, content.href)
            is RenderContent.Text -> {
                if (content.block.isBlockQuote) {
                    flushText()
                    renderTextContent(content, styleData)
                    flushText(content.block)
                } else {
                    renderTextContent(content, styleData)
                    appendSeparatorIfNeeded(
                        current = content,
                        next = renderContents.getOrNull(index + 1),
                    )
                }
            }
        }
    }
}

private fun ContentBuilder.renderTextContent(
    content: RenderContent.Text,
    styleData: StyleData,
) {
    val blockTextStyle = styleData.blockTextStyle(content.block)
    val paragraphStyle = blockTextStyle.toParagraphStyle()
    val needsParagraphStyle =
        content.block.textAlignment != null ||
            content.block.isBlockQuote ||
            content.block.headingLevel != null ||
            content.block.isFigCaption

    if (needsParagraphStyle) {
        withStyle(paragraphStyle) {
            renderRuns(content.runs, content.block, styleData, blockTextStyle)
        }
    } else {
        renderRuns(content.runs, content.block, styleData, blockTextStyle)
    }
}

private fun ContentBuilder.renderRuns(
    runs: ImmutableList<RenderRun>,
    block: RenderBlockStyle,
    styleData: StyleData,
    blockTextStyle: TextStyle,
) {
    runs.forEach { run ->
        when (run) {
            is RenderRun.Image -> {
                val imageId = context.appendImageInlineContent(run.url)
                appendInlineContent(imageId, run.alt.ifEmpty { run.url })
            }
            is RenderRun.Text -> renderTextRun(run, block, styleData, blockTextStyle)
        }
    }
}

private fun ContentBuilder.renderTextRun(
    run: RenderRun.Text,
    block: RenderBlockStyle,
    styleData: StyleData,
    blockTextStyle: TextStyle,
) {
    val spanStyle = styleData.runTextStyle(run.style, block, blockTextStyle).toSpanStyle()
    val appendContent: ContentBuilder.() -> Unit = {
        append(run.text)
    }
    val link = run.style.link

    if (link != null) {
        withLink(link) {
            withStyle(spanStyle, appendContent)
        }
    } else {
        withStyle(spanStyle, appendContent)
    }
}

private fun ContentBuilder.appendSeparatorIfNeeded(
    current: RenderContent.Text,
    next: RenderContent?,
) {
    if (next !is RenderContent.Text) return
    if (next.block.isBlockQuote) return
    when {
        current.block.headingLevel != null -> appendLine()
        current.block.isListItem -> appendLine()
        current.block.isFigCaption -> appendLine()
        current.block.isBlockQuote -> Unit
        current.block.textAlignment == RenderTextAlignment.Center -> Unit
        else -> {
            appendLine()
            appendLine()
        }
    }
}

private fun StyleData.blockTextStyle(block: RenderBlockStyle): TextStyle =
    when {
        block.headingLevel != null ->
            when (block.headingLevel) {
                1 -> h1
                2 -> h2
                3 -> h3
                4 -> h4
                5 -> h5
                else -> h6
            }
        block.isFigCaption ->
            textStyle.copy(
                fontStyle = FontStyle.Italic,
                fontSize = textStyle.fontSize * 0.7,
                textAlign = TextAlign.Center,
            )
        block.isBlockQuote ->
            textStyle.copy(
                color = color.copy(alpha = 0.7f),
            )
        block.textAlignment == RenderTextAlignment.Center ->
            textStyle.copy(
                textAlign = TextAlign.Center,
            )
        else -> textStyle
    }

private fun StyleData.runTextStyle(
    renderStyle: RenderTextStyle,
    block: RenderBlockStyle,
    blockTextStyle: TextStyle,
): TextStyle {
    var style =
        if (renderStyle.link != null) {
            linkStyle
        } else {
            blockTextStyle
        }

    if (renderStyle.bold) {
        style = style.copy(fontWeight = FontWeight.Bold)
    }
    if (renderStyle.italic) {
        style = style.copy(fontStyle = FontStyle.Italic)
    }
    if (renderStyle.strikethrough) {
        style = style.copy(textDecoration = TextDecoration.LineThrough)
    }
    if (renderStyle.underline) {
        style =
            style.copy(
                textDecoration =
                    when (style.textDecoration) {
                        TextDecoration.LineThrough -> TextDecoration.combine(
                            listOf(TextDecoration.LineThrough, TextDecoration.Underline),
                        )
                        else -> TextDecoration.Underline
                    },
            )
    }
    if (renderStyle.small) {
        style =
            style.copy(
                fontSize = style.fontSize * 0.8,
                color = style.color.takeOrElse { color }.copy(alpha = 0.7f),
            )
    }
    if (renderStyle.code || renderStyle.monospace) {
        style =
            style.copy(
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                background = color.copy(alpha = 0.1f),
            )
    }
    if (renderStyle.time) {
        style =
            style.copy(
                color = color.copy(alpha = 0.7f),
                background = color.copy(alpha = 0.05f),
            )
    }
    if (block.isFigCaption) {
        style = style.copy(textAlign = TextAlign.Center)
    }
    return style
}
