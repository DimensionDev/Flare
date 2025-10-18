package dev.dimension.flare.ui.component

import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withAnnotation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.util.fastForEach
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import com.fleeksoft.ksoup.nodes.TextNode
import dev.dimension.flare.ui.render.UiRichText


internal class RichTextState(
    val richText: UiRichText,
    val styleData: StyleData,
) {
    private val context = BuildContentAnnotatedStringContext()
    val annotatedString =
        buildContentAnnotatedString(
            element = richText.data,
            context = context,
            styleData = styleData,
        )
    val inlineContent by lazy {
        context.inlineContent
    }
    val hasBlockImage: Boolean by lazy {
        context.inlineContent.values.any {
            it is BuildContentAnnotatedStringContext.InlineType.BlockImage
        }
    }
}

internal fun buildContentAnnotatedString(
    element: Element,
    context: BuildContentAnnotatedStringContext,
    styleData: StyleData,
): AnnotatedString {
    return buildAnnotatedString {
        renderElement(
            element, 
            styleData = styleData,
            context = context,
        )
    }
}

internal class BuildContentAnnotatedStringContext {
    private var isBlockState = false
    sealed interface InlineType {
        data class Emoji(val url: String) : InlineType
        data class BlockImage(val url: String) : InlineType
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
        if (isBlockState) {
            inlineContent[id] = InlineType.BlockImage(url)
        } else {
            inlineContent[id] = InlineType.Emoji(url)
        }
        return id
    }
}

internal data class StyleData(
    val textStyle: TextStyle,
    val linkStyle: TextStyle,
    val h1: TextStyle,
    val h2: TextStyle,
    val h3: TextStyle,
    val h4: TextStyle,
    val h5: TextStyle,
    val h6: TextStyle,
)

private fun AnnotatedString.Builder.renderNode(
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

private fun AnnotatedString.Builder.renderText(
    text: String,
) {
    append(text)
}

private fun AnnotatedString.Builder.renderElement(
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
                    background = styleData.textStyle.color.copy(alpha = 0.1f),
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
                    fontStyle = FontStyle.Italic,
                    color = styleData.textStyle.color.copy(alpha = 0.7f),
                )
            withStyle(
                style.toParagraphStyle(),
            ) {
                withStyle(style.toSpanStyle()) {
                    element.childNodes().fastForEach {
                        renderNode(node = it, styleData = styleData, context = context)
                    }
                }
            }
            appendLine()
        }

        "p", "div" -> {
            withStyle(
                styleData.textStyle.toParagraphStyle(),
            ) {
                withStyle(
                    styleData.textStyle.toSpanStyle(),
                ) {
                    element.childNodes().fastForEach {
                        renderNode(node = it, styleData = styleData, context = context)
                    }
                }
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
                    appendLine()
                }
                val imageId = context.appendImageInlineContent(src)
                appendInlineContent(imageId, src)
                if (context.isInBlockState()) {
                    appendLine()
                }
            }
        }

        "strong" -> {
            pushStyle(
                styleData.textStyle.copy(fontWeight = FontWeight.Bold).toSpanStyle(),
            )
            element.childNodes().fastForEach {
                renderNode(node = it, styleData = styleData, context = context)
            }
            pop()
        }

        "em" -> {
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
            withBulletList {
                element.childNodes().fastForEach {
                    renderNode(node = it, styleData = styleData, context = context)
                }
            }
        }

        "h6" -> {
            appendLine()
            withStyle(
                styleData.h6.toParagraphStyle(),
            ) {
                withStyle(styleData.h6.toSpanStyle()) {
                    element.childNodes().fastForEach {
                        renderNode(node = it, styleData = styleData, context = context)
                    }
                }
            }
        }

        "h5" -> {
            appendLine()
            withStyle(
                styleData.h5.toParagraphStyle(),
            ) {
                withStyle(styleData.h5.toSpanStyle()) {
                    element.childNodes().fastForEach {
                        renderNode(node = it, styleData = styleData, context = context)
                    }
                }
            }
        }

        "h4" -> {
            appendLine()
            withStyle(
                styleData.h4.toParagraphStyle(),
            ) {
                withStyle(styleData.h4.toSpanStyle()) {
                    element.childNodes().fastForEach {
                        renderNode(node = it, styleData = styleData, context = context)
                    }
                }
            }
        }

        "h3" -> {
            appendLine()
            withStyle(
                styleData.h3.toParagraphStyle(),
            ) {
                withStyle(styleData.h3.toSpanStyle()) {
                    element.childNodes().fastForEach {
                        renderNode(node = it, styleData = styleData, context = context)
                    }
                }
            }
        }

        "h2" -> {
            appendLine()
            withStyle(
                styleData.h2.toParagraphStyle(),
            ) {
                withStyle(styleData.h2.toSpanStyle()) {
                    element.childNodes().fastForEach {
                        renderNode(node = it, styleData = styleData, context = context)
                    }
                }
            }
        }

        "h1" -> {
            appendLine()
            withStyle(
                styleData.h1.toParagraphStyle(),
            ) {
                withStyle(styleData.h1.toSpanStyle()) {
                    element.childNodes().fastForEach {
                        renderNode(node = it, styleData = styleData, context = context)
                    }
                }
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
        }

        else -> {
            element.childNodes().fastForEach {
                renderNode(node = it, styleData = styleData, context = context)
            }
        }
    }
}

private fun AnnotatedString.Builder.renderLink(
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
//        withLink(LinkAnnotation.Url(href)) {
                element.childNodes().fastForEach {
                    renderNode(
                        node = it,
                        styleData = styleData.copy(textStyle = styleData.linkStyle),
                        context = context,
                    )
                }
//        }
            }
        }
    }
}
