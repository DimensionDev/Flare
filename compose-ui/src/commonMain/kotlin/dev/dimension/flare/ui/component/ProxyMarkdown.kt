package dev.dimension.flare.ui.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.halilibo.richtext.commonmark.Markdown
import com.halilibo.richtext.markdown.AstBlockNodeComposer
import com.halilibo.richtext.markdown.BasicMarkdown
import com.halilibo.richtext.markdown.node.AstBlockNodeType
import com.halilibo.richtext.markdown.node.AstCode
import com.halilibo.richtext.markdown.node.AstEmphasis
import com.halilibo.richtext.markdown.node.AstHardLineBreak
import com.halilibo.richtext.markdown.node.AstHeading
import com.halilibo.richtext.markdown.node.AstImage
import com.halilibo.richtext.markdown.node.AstLink
import com.halilibo.richtext.markdown.node.AstNode
import com.halilibo.richtext.markdown.node.AstParagraph
import com.halilibo.richtext.markdown.node.AstSoftLineBreak
import com.halilibo.richtext.markdown.node.AstStrikethrough
import com.halilibo.richtext.markdown.node.AstStrongEmphasis
import com.halilibo.richtext.markdown.node.AstTableBody
import com.halilibo.richtext.markdown.node.AstTableHeader
import com.halilibo.richtext.markdown.node.AstTableRoot
import com.halilibo.richtext.markdown.node.AstText
import com.halilibo.richtext.ui.Heading
import com.halilibo.richtext.ui.RichTextScope
import com.halilibo.richtext.ui.Table
import com.halilibo.richtext.ui.string.InlineContent
import com.halilibo.richtext.ui.string.RichTextString
import com.halilibo.richtext.ui.string.Text
import com.halilibo.richtext.ui.string.withFormat
import dev.dimension.flare.data.model.ProxyRef
import dev.dimension.flare.ui.model.toUiImage
import kotlin.io.encoding.Base64

@Composable
public fun RichTextScope.ProxyMarkdown(content: String) {
    Markdown(content, astBlockNodeComposer = ProxyMarkdownNodes)
}

// The library's platform image loaders do not carry Flare's media routing policy.
private object ProxyMarkdownNodes : AstBlockNodeComposer {
    override fun predicate(astBlockNodeType: AstBlockNodeType): Boolean =
        astBlockNodeType is AstParagraph || astBlockNodeType is AstHeading || astBlockNodeType is AstTableRoot

    @Composable
    override fun RichTextScope.Compose(
        astNode: AstNode,
        visitChildren: @Composable (AstNode) -> Unit,
    ) {
        if (!astNode.hasRemoteImage()) {
            BasicMarkdown(astNode)
            return
        }
        when (val type = astNode.type) {
            is AstHeading -> Heading(type.level) { ProxyInline(astNode, Modifier.semantics { heading() }) }
            is AstTableRoot -> ProxyTable(astNode)
            else -> ProxyInline(astNode)
        }
    }
}

private fun AstNode.children(): Sequence<AstNode> = generateSequence(links.firstChild) { it.links.next }

private fun AstNode.hasRemoteImage(): Boolean {
    val pending = ArrayDeque<AstNode>()
    pending.add(this)
    while (pending.isNotEmpty()) {
        val node = pending.removeLast()
        val image = node.type as? AstImage
        if (image != null && (image.destination.startsWith("https://", true) || image.destination.startsWith("http://", true))) return true
        node.children().forEach(pending::add)
    }
    return false
}

@Composable
private fun RichTextScope.ProxyInline(
    node: AstNode,
    modifier: Modifier = Modifier,
) {
    val text = remember(node) { proxyMarkdownInline(node) }
    Text(text, modifier)
}

@Composable
private fun RichTextScope.ProxyTable(node: AstNode) {
    val header =
        node
            .children()
            .firstOrNull { it.type is AstTableHeader }
            ?.children()
            ?.firstOrNull()
    val rows =
        node
            .children()
            .firstOrNull { it.type is AstTableBody }
            ?.children()
            .orEmpty()
    Table(headerRow = {
        header?.children()?.forEach { cell { ProxyInline(it) } }
    }) {
        rows.forEach { tableRow ->
            row { tableRow.children().forEach { cell { ProxyInline(it) } } }
        }
    }
}

internal fun proxyMarkdownInline(node: AstNode): RichTextString = RichTextString.Builder().apply { appendNode(node) }.toRichTextString()

private fun RichTextString.Builder.appendNode(node: AstNode) {
    val format =
        when (val type = node.type) {
            is AstStrongEmphasis -> RichTextString.Format.Bold
            is AstEmphasis -> RichTextString.Format.Italic
            is AstStrikethrough -> RichTextString.Format.Strikethrough
            is AstLink -> RichTextString.Format.Link(type.destination)
            else -> null
        }
    when (val type = node.type) {
        is AstText -> {
            append(type.literal)
        }

        is AstCode -> {
            withFormat(RichTextString.Format.Code) { append(type.literal) }
        }

        is AstSoftLineBreak -> {
            append(" ")
        }

        is AstHardLineBreak -> {
            append("\n")
        }

        is AstImage -> {
            val description = node.plainText().ifBlank { type.title }
            appendInlineContent(
                description.ifBlank { "\uFFFD" },
                InlineContent(initialSize = { IntSize(128.dp.roundToPx(), 128.dp.roundToPx()) }) {
                    MarkdownImage(type.destination, description)
                },
            )
        }

        else -> {
            if (format == null) {
                node.children().forEach { appendNode(it) }
            } else {
                withFormat(format) { node.children().forEach { appendNode(it) } }
            }
        }
    }
}

private fun AstNode.plainText(): String = (type as? AstText)?.literal ?: children().joinToString("") { it.plainText() }

@Composable
private fun MarkdownImage(
    url: String,
    description: String,
) {
    if (url.startsWith("data:image", true) && url.contains("base64,")) {
        val bytes = remember(url) { runCatching { Base64.Mime.decode(url.substringAfter("base64,")) }.getOrNull() }
        AsyncImage(bytes, description, Modifier.fillMaxWidth(), contentScale = ContentScale.Inside)
    } else {
        NetworkImage(url.toUiImage(proxy = ProxyRef.Global), description, Modifier.fillMaxWidth(), contentScale = ContentScale.Inside)
    }
}
