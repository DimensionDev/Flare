package dev.dimension.flare.ui.component

import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withAnnotation
import androidx.compose.ui.util.fastForEach
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import com.fleeksoft.ksoup.nodes.TextNode

internal fun buildContentAnnotatedString(
    element: Element,
    textStyle: TextStyle,
    linkStyle: TextStyle,
    imageId: String,
): AnnotatedString {
    val styleData =
        StyleData(
            textStyle = textStyle,
            linkStyle = linkStyle,
        )
    return buildAnnotatedString {
        renderElement(element, styleData = styleData, imageId = imageId)
    }
}

private data class StyleData(
    val textStyle: TextStyle,
    val linkStyle: TextStyle,
)

private fun AnnotatedString.Builder.renderNode(
    node: Node,
    styleData: StyleData,
    imageId: String,
) {
    when (node) {
        is Element -> {
            this.renderElement(node, styleData = styleData, imageId = imageId)
        }

        is TextNode -> {
            renderText(node.text(), styleData.textStyle)
        }
        else -> Unit
    }
}

private fun AnnotatedString.Builder.renderText(
    text: String,
    textStyle: TextStyle,
) {
    pushStyle(
        textStyle.toSpanStyle(),
    )
    append(text)
    pop()
}

private fun AnnotatedString.Builder.renderElement(
    element: Element,
    styleData: StyleData,
    imageId: String,
) {
    when (element.tagName().lowercase()) {
        "a" -> {
            renderLink(element, styleData = styleData, imageId = imageId)
        }

        "br" -> {
            appendLine()
        }

        "p" -> {
            element.childNodes().fastForEach {
                renderNode(node = it, styleData = styleData, imageId = imageId)
            }
            val parent = element.parent()
            if (parent != null && parent.lastElementChild() != element) {
                appendLine()
            }
        }

        "span" -> {
            element.childNodes().fastForEach {
                renderNode(node = it, styleData = styleData, imageId = imageId)
            }
        }

        "emoji" -> {
//            val target = element.attributes["target"]
            val target = element.attribute("target")?.value
            if (!target.isNullOrEmpty()) {
                appendInlineContent(imageId, target)
            }
        }

        "img" -> {
//            val src = element.attributes["src"]
            val src = element.attribute("src")?.value
//            val alt = element.attributes["alt"]
            if (!src.isNullOrEmpty()) {
                appendInlineContent(imageId, src)
            }
        }

        "strong" -> {
            pushStyle(
                styleData.textStyle.copy(fontWeight = FontWeight.Bold).toSpanStyle(),
            )
            element.childNodes().fastForEach {
                renderNode(node = it, styleData = styleData, imageId = imageId)
            }
            pop()
        }

        "em" -> {
            pushStyle(
                styleData.textStyle.copy(fontStyle = FontStyle.Italic).toSpanStyle(),
            )
            element.childNodes().fastForEach {
                renderNode(node = it, styleData = styleData, imageId = imageId)
            }
            pop()
        }

        "del", "s" -> {
            pushStyle(
                styleData.textStyle.copy(textDecoration = TextDecoration.LineThrough).toSpanStyle(),
            )
            element.childNodes().fastForEach {
                renderNode(node = it, styleData = styleData, imageId = imageId)
            }
            pop()
        }

        "u" -> {
            pushStyle(
                styleData.textStyle.copy(textDecoration = TextDecoration.Underline).toSpanStyle(),
            )
            element.childNodes().fastForEach {
                renderNode(node = it, styleData = styleData, imageId = imageId)
            }
            pop()
        }

        "small" -> {
            pushStyle(
                styleData.textStyle.copy(fontSize = styleData.textStyle.fontSize * 0.8).toSpanStyle(),
            )
            element.childNodes().fastForEach {
                renderNode(node = it, styleData = styleData, imageId = imageId)
            }
            pop()
        }

        else -> {
            element.childNodes().fastForEach {
                renderNode(node = it, styleData = styleData, imageId = imageId)
            }
        }
    }
}

private fun AnnotatedString.Builder.renderLink(
    element: Element,
    styleData: StyleData,
    imageId: String,
) {
//    val href = element.attributes["href"]
    val href = element.attribute("href")?.value
    if (!href.isNullOrEmpty()) {
        withAnnotation(tag = TAG_URL, annotation = href) {
//        withLink(LinkAnnotation.Url(href)) {
            element.childNodes().fastForEach {
                renderNode(
                    node = it,
                    styleData = styleData.copy(textStyle = styleData.linkStyle),
                    imageId = imageId,
                )
            }
//        }
        }
    }
}
