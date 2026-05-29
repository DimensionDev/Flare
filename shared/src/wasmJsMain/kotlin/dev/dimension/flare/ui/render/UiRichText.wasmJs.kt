package dev.dimension.flare.ui.render

import kotlinx.collections.immutable.ImmutableList

public actual typealias PlatformText = String

internal actual fun renderPlatformText(renderRuns: ImmutableList<RenderContent>): PlatformText =
    buildString {
        renderRuns.forEach { content ->
            when (content) {
                is RenderContent.BlockImage -> appendBlockImage(content)
                is RenderContent.Text -> appendTextBlock(content)
            }
        }
    }

internal actual fun String.isRtl(): Boolean =
    any { char ->
        char in '\u0590'..'\u08FF' ||
            char in '\uFB1D'..'\uFDFF' ||
            char in '\uFE70'..'\uFEFF'
    }

private fun StringBuilder.appendTextBlock(content: RenderContent.Text) {
    val tag = content.block.htmlTag()
    append('<')
    append(tag)
    append(""" class="${content.block.classNames().escapeHtmlAttribute()}">""")
    content.runs.forEach { run ->
        when (run) {
            is RenderRun.Image -> appendInlineImage(run)
            is RenderRun.Text -> appendTextRun(run)
        }
    }
    append("</")
    append(tag)
    append('>')
}

private fun StringBuilder.appendTextRun(run: RenderRun.Text) {
    val tags = mutableListOf<String>()
    val style = run.style
    val href = style.link?.safeHref()
    if (href != null) {
        append("""<a href="${href.escapeHtmlAttribute()}" target="_blank" rel="noreferrer">""")
        tags.add("a")
    }
    if (style.bold) {
        append("<strong>")
        tags.add("strong")
    }
    if (style.italic) {
        append("<em>")
        tags.add("em")
    }
    if (style.strikethrough) {
        append("<del>")
        tags.add("del")
    }
    if (style.underline) {
        append("<u>")
        tags.add("u")
    }
    if (style.code || style.monospace) {
        append("<code>")
        tags.add("code")
    }
    if (style.small) {
        append("<small>")
        tags.add("small")
    }
    if (style.time) {
        append("<time>")
        tags.add("time")
    }

    append(run.text.escapeHtmlText())

    tags.asReversed().forEach { tag ->
        append("</")
        append(tag)
        append('>')
    }
}

private fun StringBuilder.appendInlineImage(run: RenderRun.Image) {
    val src = run.url.safeImageUrl()
    if (src == null) {
        append(run.alt.escapeHtmlText())
        return
    }
    append("""<img class="rt-inline-image" src="${src.escapeHtmlAttribute()}" alt="${run.alt.escapeHtmlAttribute()}">""")
}

private fun StringBuilder.appendBlockImage(content: RenderContent.BlockImage) {
    val src = content.url.safeImageUrl() ?: return
    val href = content.href?.safeHref()
    append("""<figure class="rt-block-image">""")
    if (href != null) {
        append("""<a href="${href.escapeHtmlAttribute()}" target="_blank" rel="noreferrer">""")
    }
    append("""<img src="${src.escapeHtmlAttribute()}" alt="">""")
    if (href != null) {
        append("</a>")
    }
    append("</figure>")
}

private fun RenderBlockStyle.htmlTag(): String =
    when {
        isBlockQuote -> "blockquote"
        isFigCaption -> "figcaption"
        headingLevel != null -> "h${headingLevel.coerceIn(1, 6)}"
        else -> "p"
    }

private fun RenderBlockStyle.classNames(): String =
    buildList {
        add("rt-block")
        if (isListItem) add("rt-list-item")
        if (isBlockQuote) add("rt-blockquote")
        if (isFigCaption) add("rt-figcaption")
        when (textAlignment) {
            RenderTextAlignment.Center -> add("rt-align-center")

            RenderTextAlignment.Start,
            null,
            -> Unit
        }
        headingLevel?.let { add("rt-heading-$it") }
    }.joinToString(" ")

private fun String.safeHref(): String? {
    val value = trim()
    if (value.isEmpty()) return null
    val lower = value.lowercase()
    return when {
        lower.startsWith("http://") -> value
        lower.startsWith("https://") -> value
        lower.startsWith("mailto:") -> value
        lower.startsWith("acct:") -> value
        lower.startsWith("flare://") -> value
        lower.startsWith("nostr:") -> value
        lower.matches(Regex("""web\+[a-z0-9.+-]+:.*""")) -> value
        value.startsWith("/") && !value.startsWith("//") -> value
        else -> null
    }
}

private fun String.safeImageUrl(): String? {
    val value = trim()
    if (value.isEmpty()) return null
    val lower = value.lowercase()
    return when {
        lower.startsWith("http://") -> value
        lower.startsWith("https://") -> value
        else -> null
    }
}

private fun String.escapeHtmlText(): String =
    buildString {
        this@escapeHtmlText.forEach { char ->
            when (char) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                else -> append(char)
            }
        }
    }

private fun String.escapeHtmlAttribute(): String =
    buildString {
        this@escapeHtmlAttribute.forEach { char ->
            when (char) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(char)
            }
        }
    }
