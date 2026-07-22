package dev.dimension.flare.data.network.tumblr

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

@Serializable
internal data class TumblrEnvelope<T>(
    val response: T? = null,
)

@Serializable
internal data class TumblrTokenResponse(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("refresh_token")
    val refreshToken: String? = null,
    @SerialName("token_type")
    val tokenType: String = "bearer",
    val scope: String? = null,
    @SerialName("expires_in")
    val expiresIn: Long? = null,
)

@Serializable
internal data class TumblrUserInfoResponse(
    val user: TumblrUser,
)

@Serializable
internal data class TumblrUser(
    val blogs: List<TumblrBlog> = emptyList(),
)

@Serializable
internal data class TumblrBlogInfoResponse(
    val blog: TumblrBlog,
)

@Serializable
internal data class TumblrBlogPage(
    val blogs: List<TumblrBlog> = emptyList(),
)

@Serializable
internal data class TumblrFollowerPage(
    val users: List<TumblrBlog> = emptyList(),
)

@Serializable
internal data class TumblrPostsPage(
    val posts: List<TumblrPost> = emptyList(),
)

@Serializable
internal data class TumblrBlog(
    val name: String? = null,
    val title: String? = null,
    val description: String? = null,
    val url: String? = null,
    val uuid: String? = null,
    val followers: Long? = null,
    @SerialName("total_posts")
    val totalPosts: Long? = null,
    val posts: Long? = null,
    val followed: Boolean? = null,
    val primary: Boolean? = null,
    val theme: TumblrBlogTheme? = null,
)

@Serializable
internal data class TumblrBlogTheme(
    @SerialName("header_image")
    val headerImage: String? = null,
)

@Serializable
internal data class TumblrPost(
    val id: Long? = null,
    @SerialName("id_string")
    val idString: String? = null,
    val state: String? = null,
    @SerialName("blog_name")
    val blogName: String? = null,
    @SerialName("blog")
    val blog: TumblrBlog? = null,
    @SerialName("post_url")
    val postUrl: String? = null,
    @SerialName("short_url")
    val shortUrl: String? = null,
    @SerialName("timestamp")
    val timestampEpochSeconds: Long? = null,
    val summary: String? = null,
    val content: List<TumblrNpfBlock> = emptyList(),
    val trail: List<TumblrTrailItem> = emptyList(),
    val layout: List<TumblrNpfLayout> = emptyList(),
    val title: String? = null,
    val body: String? = null,
    val caption: String? = null,
    val photos: List<TumblrLegacyPhoto> = emptyList(),
    val tags: List<String> = emptyList(),
    @SerialName("note_count")
    val noteCount: Long? = null,
    @SerialName("reblog_count")
    val reblogCount: Long? = null,
    @SerialName("reblogs_count")
    val reblogsCount: Long? = null,
    @SerialName("like_count")
    val likeCount: Long? = null,
    @SerialName("likes_count")
    val likesCount: Long? = null,
    @SerialName("reblog_key")
    val reblogKey: String? = null,
    val liked: Boolean? = null,
    @SerialName("can_like")
    val canLike: Boolean? = null,
    @SerialName("can_reblog")
    val canReblog: Boolean? = null,
)

@Serializable
internal data class TumblrTrailItem(
    val blog: TumblrBlog? = null,
    val content: List<TumblrNpfBlock> = emptyList(),
    val layout: List<TumblrNpfLayout> = emptyList(),
    val tags: List<String> = emptyList(),
    @SerialName("broken_blog_name")
    val brokenBlogName: String? = null,
    @SerialName("post")
    val post: TumblrTrailPost? = null,
)

@Serializable
internal data class TumblrTrailPost(
    val id: String? = null,
    val timestamp: Long? = null,
    val tags: List<String> = emptyList(),
)

@Serializable
internal data class TumblrLegacyPhoto(
    val caption: String? = null,
    @SerialName("original_size")
    val originalSize: TumblrLegacyPhotoSize? = null,
    @SerialName("alt_sizes")
    val altSizes: List<TumblrLegacyPhotoSize> = emptyList(),
)

@Serializable
internal data class TumblrLegacyPhotoSize(
    val url: String,
    val width: Int? = null,
    val height: Int? = null,
)

@Serializable
internal data class TumblrCreatePostRequest(
    val content: List<TumblrNpfBlock>,
    val state: String? = null,
)

@Serializable(with = TumblrNpfBlockSerializer::class)
internal data class TumblrNpfBlock(
    val type: String? = null,
    val subtype: String? = null,
    val text: String? = null,
    val formatting: List<TumblrNpfFormatting> = emptyList(),
    val title: String? = null,
    val caption: String? = null,
    val url: String? = null,
    val description: String? = null,
    val provider: String? = null,
    val media: List<TumblrNpfMedia> = emptyList(),
    val poster: List<TumblrNpfMedia> = emptyList(),
    @SerialName("alt_text")
    val altText: String? = null,
    @SerialName("thumbnail_url")
    val thumbnailUrl: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    @SerialName("embed_iframe")
    val embedIframe: TumblrNpfEmbedIframe? = null,
)

@Serializable
internal data class TumblrNpfFormatting(
    val type: String? = null,
    val start: Int? = null,
    val end: Int? = null,
    val url: String? = null,
    val blog: TumblrNpfFormattingBlog? = null,
)

@Serializable
internal data class TumblrNpfFormattingBlog(
    val name: String? = null,
    val url: String? = null,
)

@Serializable
internal data class TumblrNpfMedia(
    val identifier: String? = null,
    val type: String? = null,
    val url: String? = null,
    val width: Int? = null,
    val height: Int? = null,
)

@Serializable
internal data class TumblrNpfEmbedIframe(
    val url: String? = null,
)

@Serializable
internal data class TumblrNpfLayout(
    val type: String? = null,
    val display: List<TumblrNpfLayoutDisplay> = emptyList(),
    // Older trail payloads used `rows` directly instead of `display`.
    val rows: List<List<Int>> = emptyList(),
    val blocks: List<Int> = emptyList(),
    val attribution: TumblrNpfAttribution? = null,
)

@Serializable
internal data class TumblrNpfLayoutDisplay(
    val blocks: List<Int> = emptyList(),
)

@Serializable
internal data class TumblrNpfAttribution(
    val blog: TumblrBlog? = null,
)

internal object TumblrNpfBlockSerializer : KSerializer<TumblrNpfBlock> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("TumblrNpfBlock")

    override fun deserialize(decoder: Decoder): TumblrNpfBlock {
        val input = decoder as? JsonDecoder ?: return TumblrNpfBlock()
        val obj = input.decodeJsonElement() as? JsonObject ?: return TumblrNpfBlock()
        return TumblrNpfBlock(
            type = obj.stringOrNull("type"),
            subtype = obj.stringOrNull("subtype"),
            text = obj.stringOrNull("text"),
            formatting =
                obj
                    .arrayOrNull("formatting")
                    ?.mapNotNull { element ->
                        runCatching {
                            input.json.decodeFromJsonElement<TumblrNpfFormatting>(element)
                        }.getOrNull()
                    }.orEmpty(),
            title = obj.stringOrNull("title"),
            caption = obj.stringOrNull("caption"),
            url = obj.stringOrNull("url"),
            description = obj.stringOrNull("description"),
            provider = obj.stringOrNull("provider"),
            media = obj.mediaList("media"),
            poster = obj.mediaList("poster"),
            altText = obj.stringOrNull("alt_text"),
            thumbnailUrl = obj.stringOrNull("thumbnail_url"),
            width = obj.intOrNull("width"),
            height = obj.intOrNull("height"),
            embedIframe = obj.objectOrNull("embed_iframe")?.let { TumblrNpfEmbedIframe(url = it.stringOrNull("url")) },
        )
    }

    override fun serialize(
        encoder: Encoder,
        value: TumblrNpfBlock,
    ) {
        val output = encoder as? JsonEncoder ?: return
        output.encodeJsonElement(
            buildJsonObject {
                value.type?.let { put("type", it) }
                value.subtype?.let { put("subtype", it) }
                value.text?.let { put("text", it) }
                if (value.formatting.isNotEmpty()) {
                    put("formatting", output.json.encodeToJsonElement(value.formatting))
                }
                value.title?.let { put("title", it) }
                value.caption?.let { put("caption", it) }
                value.url?.let { put("url", it) }
                value.description?.let { put("description", it) }
                value.provider?.let { put("provider", it) }
                if (value.media.isNotEmpty()) {
                    put("media", value.media.toNpfMediaJsonElement(asArray = value.type != "video" && value.type != "audio"))
                }
                if (value.poster.isNotEmpty()) {
                    put("poster", value.poster.toNpfMediaJsonElement(asArray = true))
                }
                value.altText?.let { put("alt_text", it) }
                value.thumbnailUrl?.let { put("thumbnail_url", it) }
                value.width?.let { put("width", it) }
                value.height?.let { put("height", it) }
                value.embedIframe?.url?.let { url ->
                    put(
                        "embed_iframe",
                        buildJsonObject {
                            put("url", url)
                        },
                    )
                }
            },
        )
    }
}

private fun List<TumblrNpfMedia>.toNpfMediaJsonElement(asArray: Boolean) =
    if (asArray) {
        JsonArray(map { tumblrNpfMediaJsonObject(it) })
    } else {
        tumblrNpfMediaJsonObject(first())
    }

private fun tumblrNpfMediaJsonObject(media: TumblrNpfMedia): JsonObject =
    buildJsonObject {
        media.identifier?.let { put("identifier", it) }
        media.type?.let { put("type", it) }
        media.url?.let { put("url", it) }
        media.width?.let { put("width", it) }
        media.height?.let { put("height", it) }
    }

private fun JsonObject.mediaList(name: String): List<TumblrNpfMedia> =
    when (val value = get(name)) {
        is JsonObject -> {
            listOf(value.toTumblrNpfMedia())
        }

        is JsonArray -> {
            value.mapNotNull { element ->
                (element as? JsonObject)?.toTumblrNpfMedia()
                    ?: (element as? JsonPrimitive)?.contentOrNull?.let { TumblrNpfMedia(url = it) }
            }
        }

        is JsonPrimitive -> {
            value.contentOrNull?.let { listOf(TumblrNpfMedia(url = it)) }.orEmpty()
        }

        else -> {
            emptyList()
        }
    }

private fun JsonObject.toTumblrNpfMedia(): TumblrNpfMedia =
    TumblrNpfMedia(
        identifier = stringOrNull("identifier"),
        type = stringOrNull("type"),
        url = stringOrNull("url"),
        width = intOrNull("width"),
        height = intOrNull("height"),
    )

private fun JsonObject.objectOrNull(name: String): JsonObject? = get(name) as? JsonObject

private fun JsonObject.arrayOrNull(name: String): JsonArray? = get(name) as? JsonArray

private fun JsonObject.stringOrNull(name: String): String? = (get(name) as? JsonPrimitive)?.contentOrNull

private fun JsonObject.intOrNull(name: String): Int? = (get(name) as? JsonPrimitive)?.intOrNull
