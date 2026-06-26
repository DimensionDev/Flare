package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable
import dev.dimension.flare.common.SerializableImmutableList
import dev.dimension.flare.common.SerializableImmutableMap
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.render.RenderContent
import dev.dimension.flare.ui.render.UiDateTime
import dev.dimension.flare.ui.render.UiRichText
import dev.dimension.flare.ui.render.plainText
import dev.dimension.flare.ui.render.uiRichTextOf
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.native.HiddenFromObjC

@Serializable
@Immutable
public data class UiArticle public constructor(
    val key: String,
    val title: String,
    val content: UiArticleContent,
    val cover: UiMedia.Image? = null,
    val publishDate: UiDateTime? = null,
    val author: UiArticleAuthor? = null,
    val sourceUrl: String? = null,
)

@Serializable
@Immutable
public sealed interface UiArticleAuthor {
    @Serializable
    @SerialName("profile")
    @Immutable
    public data class Profile public constructor(
        val profile: UiProfile,
    ) : UiArticleAuthor

    @Serializable
    @SerialName("rss")
    @Immutable
    public data class Rss public constructor(
        val siteName: String? = null,
        val byline: String? = null,
        val iconUrl: String? = null,
    ) : UiArticleAuthor
}

@Serializable
@Immutable
public data class UiArticleContent public constructor(
    val blocks: SerializableImmutableList<UiArticleBlock> = persistentListOf(),
    val rawText: String = "",
) {
    val isEmpty: Boolean
        get() = blocks.isEmpty()
}

@Serializable
@Immutable
public sealed interface UiArticleBlock {
    public val key: String

    @Serializable
    @SerialName("text")
    @Immutable
    public data class Text public constructor(
        override val key: String,
        val content: RenderContent.Text,
    ) : UiArticleBlock {
        public val richText: UiRichText by lazy {
            uiRichTextOf(listOf(content))
        }
    }

    @Serializable
    @SerialName("image")
    @Immutable
    public data class Image public constructor(
        override val key: String,
        val media: UiMedia.Image,
    ) : UiArticleBlock

    @Serializable
    @SerialName("video")
    @Immutable
    public data class Video public constructor(
        override val key: String,
        val media: UiMedia.Video,
    ) : UiArticleBlock

    @Serializable
    @SerialName("file")
    @Immutable
    public data class File public constructor(
        override val key: String,
        val name: String,
        val url: String,
        val sizeBytes: Long? = null,
        val extension: String? = null,
        val customHeaders: SerializableImmutableMap<String, String>? = null,
    ) : UiArticleBlock

    @Serializable
    @SerialName("embed")
    @Immutable
    public data class Embed public constructor(
        override val key: String,
        val url: String? = null,
        val title: String? = null,
        val description: String? = null,
        val imageUrl: String? = null,
        val htmlFallback: String? = null,
    ) : UiArticleBlock

    @Serializable
    @SerialName("content_gate")
    @Immutable
    public data class ContentGate public constructor(
        override val key: String,
        val reason: UiArticleContentGateReason,
        val actionUrl: String? = null,
    ) : UiArticleBlock
}

@Serializable
@Immutable
public sealed interface UiArticleContentGateReason {
    @Serializable
    @SerialName("subscription_required")
    @Immutable
    public data class SubscriptionRequired public constructor(
        val platformType: PlatformType,
        val feeRequired: Int? = null,
    ) : UiArticleContentGateReason
}

@HiddenFromObjC
public fun uiArticleContentOf(
    blocks: List<UiArticleBlock>,
    rawText: String? = null,
): UiArticleContent =
    UiArticleContent(
        blocks = blocks.toImmutableList(),
        rawText =
            rawText
                ?: blocks
                    .map { it.plainText() }
                    .filter { it.isNotBlank() }
                    .joinToString(separator = "\n")
                    .trim(),
    )

@HiddenFromObjC
public fun UiArticleBlock.plainText(): String =
    when (this) {
        is UiArticleBlock.Text -> {
            content.plainText()
        }

        is UiArticleBlock.Image -> {
            media.description.orEmpty()
        }

        is UiArticleBlock.Video -> {
            ""
        }

        is UiArticleBlock.File -> {
            name
        }

        is UiArticleBlock.Embed -> {
            listOfNotNull(title, description, url)
                .joinToString(separator = "\n")
        }

        is UiArticleBlock.ContentGate -> {
            ""
        }
    }
