package dev.dimension.flare.ui.render

import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import com.fleeksoft.ksoup.nodes.TextNode
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

internal fun mapHtmlToRenderContents(element: Element): ImmutableList<RenderContent> {
    val builder = RenderRunBuilder()
    builder.renderElement(
        element = element,
        style = RenderTextStyle(),
        block = RenderBlockStyle(),
    )
    return builder.build()
}

private class RenderRunBuilder {
    private val contents = mutableListOf<RenderContent>()
    private val currentRuns = mutableListOf<RenderRun>()
    private var currentBlock = RenderBlockStyle()
    private var isInBlockImageState = false

    fun build(): ImmutableList<RenderContent> {
        flushTextContent()
        return contents.toImmutableList()
    }

    fun renderNode(
        node: Node,
        style: RenderTextStyle,
        block: RenderBlockStyle,
    ) {
        when (node) {
            is Element -> renderElement(node, style, block)
            is TextNode -> appendText(node.getWholeText(), style, block)
        }
    }

    fun renderElement(
        element: Element,
        style: RenderTextStyle,
        block: RenderBlockStyle,
    ) {
        when (element.tagName().lowercase()) {
            "a" -> renderChildren(element, style.copy(link = element.attr("href").ifEmpty { null }), block)
            "strong", "b" -> renderChildren(element, style.copy(bold = true), block)
            "em", "i" -> renderChildren(element, style.copy(italic = true), block)
            "br" -> appendText("\n", style, block)
            "p", "div" -> renderBlock(element, style, block)
            "span" -> renderChildren(element, style, block)
            "del", "s" -> renderChildren(element, style.copy(strikethrough = true), block)
            "code" -> renderChildren(element, style.copy(monospace = true, code = true), block)
            "blockquote" -> renderBlock(element, style, block.copy(isBlockQuote = true))
            "u" -> renderChildren(element, style.copy(underline = true), block)
            "small" -> renderChildren(element, style.copy(small = true), block)
            "center" -> renderBlock(element, style, block.copy(textAlignment = RenderTextAlignment.Center))
            "li" -> {
                val listBlock = block.copy(isListItem = true)
                runInBlock(listBlock) {
                    appendText("\u2022 ", style, listBlock)
                    renderChildren(element, style, listBlock)
                }
            }
            "ul" -> renderChildren(element, style, block)
            "h1" -> renderBlock(element, style, block.copy(headingLevel = 1))
            "h2" -> renderBlock(element, style, block.copy(headingLevel = 2))
            "h3" -> renderBlock(element, style, block.copy(headingLevel = 3))
            "h4" -> renderBlock(element, style, block.copy(headingLevel = 4))
            "h5" -> renderBlock(element, style, block.copy(headingLevel = 5))
            "h6" -> renderBlock(element, style, block.copy(headingLevel = 6))
            "emoji" ->
                appendImage(
                    url = element.attr("target"),
                    alt = element.attr("alt"),
                    block = block,
                )
            "figure" -> {
                val previousBlockState = isInBlockImageState
                isInBlockImageState = true
                renderChildren(element, style, block)
                isInBlockImageState = previousBlockState
            }
            "img" -> {
                val src = element.attr("src")
                if (isInBlockImageState) {
                    appendBlockImage(
                        url = src,
                        href = element.attr("href").ifEmpty { null },
                    )
                } else {
                    appendImage(
                        url = src,
                        alt = element.attr("alt"),
                        block = block,
                    )
                }
            }
            "figcaption" -> {
                renderBlock(
                    element = element,
                    style = style.copy(italic = true, small = true),
                    block =
                        block.copy(
                            textAlignment = RenderTextAlignment.Center,
                            isFigCaption = true,
                        ),
                )
            }
            "time" -> renderChildren(element, style.copy(time = true), block)
            else -> renderChildren(element, style, block)
        }
    }

    private fun renderChildren(
        element: Element,
        style: RenderTextStyle,
        block: RenderBlockStyle,
    ) {
        element.childNodes().forEach { renderNode(it, style, block) }
    }

    private fun renderBlock(
        element: Element,
        style: RenderTextStyle,
        block: RenderBlockStyle,
    ) {
        runInBlock(block) {
            renderChildren(element, style, block)
        }
    }

    private inline fun runInBlock(
        block: RenderBlockStyle,
        content: () -> Unit,
    ) {
        flushTextContent()
        val previousBlock = currentBlock
        currentBlock = block
        try {
            content()
        } finally {
            flushTextContent()
            currentBlock = previousBlock
        }
    }

    private fun appendText(
        text: String,
        style: RenderTextStyle,
        block: RenderBlockStyle,
    ) {
        if (text.isEmpty()) return
        ensureBlock(block)
        val lastRun = currentRuns.lastOrNull()
        if (lastRun is RenderRun.Text && lastRun.style == style) {
            currentRuns[currentRuns.lastIndex] = lastRun.copy(text = lastRun.text + text)
        } else {
            currentRuns.add(RenderRun.Text(text = text, style = style))
        }
    }

    private fun appendImage(
        url: String,
        alt: String,
        block: RenderBlockStyle,
    ) {
        ensureBlock(block)
        currentRuns.add(RenderRun.Image(url = url, alt = alt))
    }

    private fun appendBlockImage(
        url: String,
        href: String?,
    ) {
        flushTextContent()
        contents.add(RenderContent.BlockImage(url = url, href = href))
    }

    private fun flushTextContent() {
        if (currentRuns.isEmpty()) return
        contents.add(
            RenderContent.Text(
                runs = currentRuns.toImmutableList(),
                block = currentBlock,
            ),
        )
        currentRuns.clear()
    }

    private fun ensureBlock(block: RenderBlockStyle) {
        if (currentRuns.isNotEmpty() && currentBlock != block) {
            flushTextContent()
        }
        currentBlock = block
    }
}
