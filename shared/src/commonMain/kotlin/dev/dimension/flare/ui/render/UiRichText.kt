package dev.dimension.flare.ui.render

import androidx.compose.runtime.Immutable
import de.cketti.codepoints.codePointCount
import dev.dimension.flare.common.SerializableImmutableList
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
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
                is RenderContent.BlockImage -> {
                    false
                }

                is RenderContent.Text -> {
                    content.runs.all { run ->
                        when (run) {
                            is RenderRun.Image -> false
                            is RenderRun.Text -> run.text.isEmpty()
                        }
                    }
                }
            }
        }
    public val isLongText: Boolean = innerText.codePointCount() > 500
    public val platformText: PlatformText by lazy {
        renderPlatformText(renderRuns)
    }
}

internal fun uiRichTextOf(
    renderRuns: List<RenderContent>,
    raw: String? = null,
    innerText: String? = null,
    imageUrls: List<String>? = null,
    sourceLanguages: List<String> = emptyList(),
): UiRichText {
    val contents = renderRuns.toImmutableList()
    val resolvedInnerText = innerText ?: contents.joinToString(separator = "") { it.plainText() }
    return UiRichText(
        renderRuns = contents,
        isRtl = resolvedInnerText.resolveRtl(sourceLanguages),
        raw = raw ?: resolvedInnerText,
        innerText = resolvedInnerText,
        imageUrls = (imageUrls ?: contents.imageUrls()).toImmutableList(),
    )
}

public fun String.toUiPlainText(sourceLanguages: List<String> = emptyList()): UiRichText =
    UiRichText(
        renderRuns =
            persistentListOf(
                RenderContent.Text(
                    runs =
                        persistentListOf(
                            RenderRun.Text(text = this),
                        ),
                ),
            ),
        isRtl = resolveRtl(sourceLanguages),
        raw = this,
        innerText = this,
        imageUrls = persistentListOf<String>(),
    )

public fun UiRichText.toTranslatableText(): String {
    val builder = StringBuilder()
    var appendedTextBlock = false
    renderRuns.forEach { content ->
        when (content) {
            is RenderContent.BlockImage -> {
                Unit
            }

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

                is RenderRun.Text -> {
                    append(run.text)
                }
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
            is RenderContent.BlockImage -> {
                listOf(content.url)
            }

            is RenderContent.Text -> {
                content.runs.mapNotNull { run ->
                    when (run) {
                        is RenderRun.Image -> run.url
                        is RenderRun.Text -> null
                    }
                }
            }
        }
    }

internal fun String.resolveRtl(sourceLanguages: List<String> = emptyList()): Boolean {
    sourceLanguages.firstNotNullOfOrNull { it.languageIsRtl() }?.let {
        return it
    }
    if (isBlank() || isLatinText()) {
        return false
    }
    if (!hasStrongRtlCodePoint()) {
        return false
    }
    return isRtl()
}

private fun String.languageIsRtl(): Boolean? {
    val language =
        substringBefore('-')
            .substringBefore('_')
            .lowercase()
            .takeIf { it.isNotBlank() }
            ?: return null
    if (language in unknownLanguageCodes) {
        return null
    }
    return language in rtlLanguageCodes
}

private fun String.isLatinText(): Boolean =
    all { char ->
        !char.isLetter() || char.isLatinLetter()
    }

private fun Char.isLatinLetter(): Boolean =
    this in '\u0041'..'\u005A' ||
        this in '\u0061'..'\u007A' ||
        this in '\u00C0'..'\u024F' ||
        this in '\u1E00'..'\u1EFF'

private fun String.hasStrongRtlCodePoint(): Boolean {
    var index = 0
    while (index < length) {
        val codePoint = codePointAt(index)
        if (codePoint.isStrongRtlCodePoint()) {
            return true
        }
        index += codePoint.charCount()
    }
    return false
}

private fun String.codePointAt(index: Int): Int {
    val high = this[index]
    if (high.isHighSurrogate() && index + 1 < length) {
        val low = this[index + 1]
        if (low.isLowSurrogate()) {
            return ((high.code - HIGH_SURROGATE_START) shl 10) +
                (low.code - LOW_SURROGATE_START) +
                SUPPLEMENTARY_CODE_POINT_START
        }
    }
    return high.code
}

private fun Int.charCount(): Int =
    if (this >= SUPPLEMENTARY_CODE_POINT_START) {
        2
    } else {
        1
    }

private fun Int.isStrongRtlCodePoint(): Boolean =
    rtlCodePointRanges.any { range ->
        this in range
    }

private val rtlCodePointRanges =
    listOf(
        0x0590..0x05FF, // Hebrew
        0x0600..0x06FF, // Arabic
        0x0700..0x074F, // Syriac
        0x0750..0x077F, // Arabic Supplement
        0x0780..0x07BF, // Thaana
        0x07C0..0x07FF, // NKo
        0x0800..0x083F, // Samaritan
        0x0840..0x085F, // Mandaic
        0x0860..0x086F, // Syriac Supplement
        0x0870..0x089F, // Arabic Extended-B
        0x08A0..0x08FF, // Arabic Extended-A
        0xFB1D..0xFB4F, // Hebrew Presentation Forms
        0xFB50..0xFDFF, // Arabic Presentation Forms-A
        0xFE70..0xFEFF, // Arabic Presentation Forms-B
        0x10800..0x10FFF, // Historical RTL scripts
        0x1E800..0x1E95F, // Mende Kikakui, Adlam
    )

private const val HIGH_SURROGATE_START = 0xD800
private const val LOW_SURROGATE_START = 0xDC00
private const val SUPPLEMENTARY_CODE_POINT_START = 0x10000

private val rtlLanguageCodes =
    setOf(
        "ar",
        "arc",
        "dv",
        "fa",
        "ha",
        "he",
        "iw",
        "ks",
        "ku",
        "nqo",
        "pa",
        "ps",
        "sd",
        "syr",
        "ug",
        "ur",
        "yi",
    )

private val unknownLanguageCodes =
    setOf(
        "mis",
        "mul",
        "qaa",
        "und",
        "zxx",
    )

internal expect fun String.isRtl(): Boolean
