package dev.dimension.flare.ui.render

import androidx.compose.runtime.Immutable
import de.cketti.codepoints.codePointCount
import dev.dimension.flare.common.SerializableImmutableList
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.Serializable

public expect class PlatformText

internal expect fun renderPlatformText(renderRuns: ImmutableList<RenderContent>): PlatformText

@Serializable
public sealed class RenderContent {
    @Serializable
    public data class Text(
        val runs: SerializableImmutableList<RenderRun>,
        val block: RenderBlockStyle = RenderBlockStyle(),
    ) : RenderContent()

    @Serializable
    public data class BlockImage(
        val url: String,
        val href: String?,
    ) : RenderContent()
}

@Serializable
public sealed class RenderRun {
    @Serializable
    public data class Text(
        val text: String,
        val style: RenderTextStyle = RenderTextStyle(),
    ) : RenderRun()

    @Serializable
    public data class Image(
        val url: String,
        val alt: String,
    ) : RenderRun()
}

@Serializable
public data class RenderTextStyle(
    val link: String? = null,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val strikethrough: Boolean = false,
    val monospace: Boolean = false,
    val code: Boolean = false,
    val underline: Boolean = false,
    val small: Boolean = false,
    val time: Boolean = false,
)

@Serializable
public data class RenderBlockStyle(
    val headingLevel: Int? = null,
    val textAlignment: RenderTextAlignment? = null,
    val isListItem: Boolean = false,
    val isBlockQuote: Boolean = false,
    val isFigCaption: Boolean = false,
)

@Serializable
public enum class RenderTextAlignment {
    Start,
    Center,
}

@Serializable
@Immutable
public data class UiRichText(
    public val renderRuns: SerializableImmutableList<RenderContent>,
    val isRtl: Boolean,
    public val raw: String,
    public val innerText: String,
    public val imageUrls: SerializableImmutableList<String>,
) {
    public val isEmpty: Boolean =
        renderRuns.all { content ->
            when (content) {
                is RenderContent.BlockImage -> false
                is RenderContent.Text ->
                    content.runs.all { run ->
                        when (run) {
                            is RenderRun.Image -> false
                            is RenderRun.Text -> run.text.isEmpty()
                        }
                    }
            }
        }
    public val isLongText: Boolean = innerText.codePointCount() > 480
    public val platformText: PlatformText by lazy {
        renderPlatformText(renderRuns)
    }
}

internal fun uiRichTextOf(
    renderRuns: List<RenderContent>,
    raw: String? = null,
    innerText: String? = null,
    imageUrls: List<String>? = null,
): UiRichText {
    val contents = renderRuns.toImmutableList()
    val resolvedInnerText = innerText ?: contents.joinToString(separator = "") { it.plainText() }
    return UiRichText(
        renderRuns = contents,
        isRtl = resolvedInnerText.isRtl(),
        raw = raw ?: resolvedInnerText,
        innerText = resolvedInnerText,
        imageUrls = (imageUrls ?: contents.imageUrls()).toImmutableList(),
    )
}

public fun String.toUiPlainText(): UiRichText =
    UiRichText(
        renderRuns =
            listOf(
                RenderContent.Text(
                    runs =
                        listOf(
                            RenderRun.Text(text = this),
                        ).toImmutableList(),
                ),
            ).toImmutableList(),
        isRtl = isRtl(),
        raw = this,
        innerText = this,
        imageUrls = emptyList<String>().toImmutableList(),
    )

public fun UiRichText.toTranslatableText(): String {
    val builder = StringBuilder()
    var appendedTextBlock = false
    renderRuns.forEach { content ->
        when (content) {
            is RenderContent.BlockImage -> Unit
            is RenderContent.Text -> {
                val blockText = content.plainText().trim()
                if (blockText.isNotEmpty()) {
                    if (appendedTextBlock) {
                        builder.append('\n')
                    }
                    builder.append(blockText)
                    appendedTextBlock = true
                }
            }
        }
    }
    return builder.toString().trim()
}

public fun RenderContent.Text.plainText(): String =
    buildString {
        runs.forEach { run ->
            when (run) {
                is RenderRun.Image -> {
                    if (run.alt.isNotBlank()) {
                        append(run.alt)
                    }
                }
                is RenderRun.Text -> append(run.text)
            }
        }
    }

private fun RenderContent.plainText(): String =
    when (this) {
        is RenderContent.BlockImage -> ""
        is RenderContent.Text -> plainText()
    }

private fun List<RenderContent>.imageUrls(): List<String> =
    flatMap { content ->
        when (content) {
            is RenderContent.BlockImage -> listOf(content.url)
            is RenderContent.Text ->
                content.runs.mapNotNull { run ->
                    when (run) {
                        is RenderRun.Image -> run.url
                        is RenderRun.Text -> null
                    }
                }
        }
    }

internal expect fun String.isRtl(): Boolean
