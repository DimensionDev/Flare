package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable
import dev.dimension.flare.common.SerializableImmutableMap
import dev.dimension.flare.data.model.ProxyRef
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import okio.ByteString.Companion.encodeUtf8
import kotlin.native.HiddenFromObjC

@Serializable
@Immutable
public sealed interface UiMedia {
    public val proxy: ProxyRef
    public val url: String
    public val downloadUrl: String?
        get() = null
    public val urlForDownload: String
        get() = downloadUrl ?: url
    public val description: String?
    public val customHeaders: SerializableImmutableMap<String, String>?

    public val isRemote: Boolean get() = url.startsWith("https://", true) || url.startsWith("http://", true)

    public val cacheKey: String
        get() = cacheKeyFor(url)

    public fun cacheKeyFor(url: String): String {
        val headers = customHeaders.orEmpty().entries.sortedBy { it.key.lowercase() }
        val authenticated = headers.any { it.key.equals("Authorization", true) || it.key.equals("Cookie", true) }
        return buildString {
            append(url)
            if (headers.isNotEmpty()) {
                append(
                    "|" +
                        headers
                            .joinToString("\n") { "${it.key.lowercase()}:${it.value}" }
                            .encodeUtf8()
                            .sha256()
                            .hex(),
                )
            }
            if (authenticated) append("|$proxy")
        }
    }

    public val preview: Image?
        get() =
            when (this) {
                is Image -> copy(url = previewUrl)
                is Video -> thumbnailUrl.toUiImage(customHeaders, proxy)
                is Gif -> previewUrl.toUiImage(customHeaders, proxy)
                is Ugoira -> previewUrl.toUiImage(customHeaders, proxy)
                is Audio -> previewUrl.toUiImage(customHeaders, proxy)
            }

    @Serializable
    @Immutable
    public data class Image public constructor(
        override val url: String,
        val previewUrl: String,
        override val description: String?,
        val height: Float,
        val width: Float,
        val sensitive: Boolean,
        override val customHeaders: SerializableImmutableMap<String, String>? = null,
        @Transient override val proxy: ProxyRef = ProxyRef.Unbound,
    ) : UiMedia {
        internal constructor(
            url: String,
            customHeaders: SerializableImmutableMap<String, String>? = null,
            proxy: ProxyRef = ProxyRef.Unbound,
        ) : this(
            url = url,
            previewUrl = url,
            description = null,
            height = 0f,
            width = 0f,
            sensitive = false,
            customHeaders = customHeaders,
            proxy = proxy,
        )

        val aspectRatio: Float
            get() = (width / (height.takeUnless { it == 0f } ?: 1f)).takeUnless { it == 0f } ?: 1f
    }

    @Serializable
    @Immutable
    public data class Video public constructor(
        override val url: String,
        val thumbnailUrl: String,
        override val description: String?,
        val height: Float,
        val width: Float,
        override val customHeaders: SerializableImmutableMap<String, String>? = null,
        @Transient override val proxy: ProxyRef = ProxyRef.Unbound,
        override val downloadUrl: String? = null,
    ) : UiMedia {
        val aspectRatio: Float
            get() = (width / (height.takeUnless { it == 0f } ?: 1f)).takeUnless { it == 0f } ?: 1f
    }

    @Serializable
    @Immutable
    public data class Gif public constructor(
        override val url: String,
        val previewUrl: String,
        override val description: String?,
        val height: Float,
        val width: Float,
        override val customHeaders: SerializableImmutableMap<String, String>? = null,
        @Transient override val proxy: ProxyRef = ProxyRef.Unbound,
    ) : UiMedia {
        val aspectRatio: Float
            get() = (width / (height.takeUnless { it == 0f } ?: 1f)).takeUnless { it == 0f } ?: 1f
    }

    @Serializable
    @Immutable
    public data class Ugoira public constructor(
        val statusKey: MicroBlogKey,
        val accountKey: MicroBlogKey,
        val previewUrl: String,
        val originalFrameUrl: String?,
        override val description: String?,
        val height: Float,
        val width: Float,
        val sensitive: Boolean,
        override val customHeaders: SerializableImmutableMap<String, String>? = null,
        @Transient override val proxy: ProxyRef = ProxyRef.forAccount(accountKey),
    ) : UiMedia {
        override val url: String get() = "https://www.pixiv.net/artworks/${statusKey.id}"

        // Ugoira is a frame sequence. Saving it requires the platform MP4 encoder.
        override val urlForDownload: String get() = error("Ugoira must be exported as MP4")
        val aspectRatio: Float
            get() = (width / height).takeIf { it.isFinite() && it > 0f } ?: 1f
    }

    @Serializable
    @Immutable
    public data class Audio public constructor(
        override val url: String,
        override val description: String?,
        val previewUrl: String?,
        override val customHeaders: SerializableImmutableMap<String, String>? = null,
        @Transient override val proxy: ProxyRef = ProxyRef.Unbound,
    ) : UiMedia
}

@HiddenFromObjC
public fun String?.toUiImage(
    customHeaders: SerializableImmutableMap<String, String>? = null,
    proxy: ProxyRef = ProxyRef.Unbound,
): UiMedia.Image? =
    this
        ?.takeIf { it.isNotBlank() }
        ?.let {
            UiMedia.Image(
                url = it,
                previewUrl = it,
                description = null,
                height = 0f,
                width = 0f,
                sensitive = false,
                customHeaders = customHeaders,
                proxy = proxy,
            )
        }
