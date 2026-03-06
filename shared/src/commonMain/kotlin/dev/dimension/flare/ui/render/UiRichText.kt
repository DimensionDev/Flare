package dev.dimension.flare.ui.render

import androidx.compose.runtime.Immutable
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.TextNode
import de.cketti.codepoints.codePointCount
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

public expect class PlatformText

internal expect fun UiRichText.renderPlatformText(): PlatformText

@Serializable(with = UiRichTextSerializer::class)
@Immutable
public data class UiRichText(
    val data: Element,
    val isRtl: Boolean,
) {
    public val innerText: String = data.wholeText()
    val raw: String by lazy {
        data
            .nodeStream()
            .joinToString("") { node ->
                when {
                    node is TextNode -> node.getWholeText()
                    node.nameIs("br") -> "\n"
                    node.nameIs("img") -> node.attr("alt")
                    node.nameIs("emoji") -> node.attr("alt")
                    else -> ""
                }
            }
    }
    val html: String = data.compactHtml()
    public val isEmpty: Boolean = raw.isEmpty() && data.getAllElements().size <= 1
    public val isLongText: Boolean = innerText.codePointCount() > 480

    public val imageUrls: ImmutableList<String> =
        data
            .getElementsByTag("img")
            .mapNotNull { it.attr("src").ifEmpty { null } }
            .plus(
                data
                    .getElementsByTag("emoji")
                    .mapNotNull { it.attr("target").ifEmpty { null } },
            ).toImmutableList()

    public val platformText: PlatformText by lazy {
        renderPlatformText()
    }
}

internal object UiRichTextSerializer : KSerializer<UiRichText> {
    override val descriptor by lazy {
        PrimitiveSerialDescriptor("UiRichText", PrimitiveKind.STRING)
    }

    override fun serialize(
        encoder: Encoder,
        value: UiRichText,
    ) {
        encoder.encodeString(value.html)
    }

    override fun deserialize(decoder: Decoder): UiRichText {
        val html = decoder.decodeString()
        return parseHtml(html).toUi()
    }
}

private fun Element.compactHtml(): String {
    val root = clone()
    val document = Ksoup.parse("")
    document.outputSettings().prettyPrint(false)
    document.body().appendChild(root)
    return root.html()
}

internal fun Element.toUi(): UiRichText =
    UiRichText(
        data = this,
        isRtl = text().isRtl(),
    )

internal fun parseHtml(html: String): Element = Ksoup.parse(html).body()

internal expect fun String.isRtl(): Boolean
