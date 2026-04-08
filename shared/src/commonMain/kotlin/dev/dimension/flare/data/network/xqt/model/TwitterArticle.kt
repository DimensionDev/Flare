package dev.dimension.flare.data.network.xqt.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class TwitterArticle(
    @SerialName("article_results")
    val articleResults: TwitterArticleResults? = null,
)

@Serializable
internal data class TwitterArticleResults(
    val result: TwitterArticleResult? = null,
)

@Serializable
internal data class TwitterArticleResult(
    @SerialName("content_state")
    val contentState: TwitterArticleContentState? = null,
    @SerialName("cover_media")
    val coverMedia: TwitterArticleMedia? = null,
    @SerialName("media_entities")
    val mediaEntities: List<TwitterArticleMedia> = emptyList(),
    @SerialName("preview_text")
    val previewText: String? = null,
    @SerialName("rest_id")
    val restId: String? = null,
    val title: String? = null,
)

@Serializable
internal data class TwitterArticleContentState(
    val blocks: List<TwitterArticleBlock> = emptyList(),
    val entityMap: List<TwitterArticleEntityEntry> = emptyList(),
)

@Serializable
internal data class TwitterArticleBlock(
    val data: TwitterArticleBlockData = TwitterArticleBlockData(),
    val entityRanges: List<TwitterArticleEntityRange> = emptyList(),
    val inlineStyleRanges: List<TwitterArticleInlineStyleRange> = emptyList(),
    val key: String? = null,
    val text: String = "",
    val type: String = "unstyled",
)

@Serializable
internal data class TwitterArticleBlockData(
    val urls: List<TwitterArticleBlockUrl> = emptyList(),
)

@Serializable
internal data class TwitterArticleBlockUrl(
    val text: String? = null,
    val fromIndex: Int? = null,
    val toIndex: Int? = null,
)

@Serializable
internal data class TwitterArticleEntityRange(
    val key: Int,
    val length: Int,
    val offset: Int,
)

@Serializable
internal data class TwitterArticleInlineStyleRange(
    val length: Int,
    val offset: Int,
    val style: String,
)

@Serializable
internal data class TwitterArticleEntityEntry(
    val key: String? = null,
    val value: TwitterArticleEntity? = null,
)

@Serializable
internal data class TwitterArticleEntity(
    val data: TwitterArticleEntityData = TwitterArticleEntityData(),
    val mutability: String? = null,
    val type: String? = null,
)

@Serializable
internal data class TwitterArticleEntityData(
    val caption: String? = null,
    val entityKey: String? = null,
    val mediaItems: List<TwitterArticleMediaItem> = emptyList(),
    val url: String? = null,
)

@Serializable
internal data class TwitterArticleMediaItem(
    val localMediaId: String? = null,
    val mediaCategory: String? = null,
    val mediaId: String? = null,
)

@Serializable
internal data class TwitterArticleMedia(
    val id: String? = null,
    @SerialName("media_id")
    val mediaId: String? = null,
    @SerialName("media_info")
    val mediaInfo: TwitterArticleMediaInfo? = null,
    @SerialName("media_key")
    val mediaKey: String? = null,
)

@Serializable
internal data class TwitterArticleMediaInfo(
    @SerialName("__typename")
    val typeName: String? = null,
    @SerialName("original_img_height")
    val originalImgHeight: Int? = null,
    @SerialName("original_img_url")
    val originalImgUrl: String? = null,
    @SerialName("original_img_width")
    val originalImgWidth: Int? = null,
    @SerialName("preview_image")
    val previewImage: TwitterArticlePreviewImage? = null,
    val variants: List<TwitterArticleMediaVariant> = emptyList(),
)

@Serializable
internal data class TwitterArticlePreviewImage(
    @SerialName("original_img_height")
    val originalImgHeight: Int? = null,
    @SerialName("original_img_url")
    val originalImgUrl: String? = null,
    @SerialName("original_img_width")
    val originalImgWidth: Int? = null,
)

@Serializable
internal data class TwitterArticleMediaVariant(
    @SerialName("bit_rate")
    val bitRate: Int? = null,
    @SerialName("content_type")
    val contentType: String? = null,
    val url: String? = null,
)
