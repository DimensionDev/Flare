package dev.dimension.flare.data.network.fanbox

import io.ktor.http.Url
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonTransformingSerializer

@Serializable
internal data class FanboxMetaDataEntity(
    @SerialName("apiUrl")
    val apiUrl: String? = null,
    @SerialName("context")
    val context: Context? = null,
    @SerialName("csrfToken")
    val csrfToken: String,
) {
    @Serializable
    internal data class Context(
        @SerialName("user")
        val user: User? = null,
    ) {
        @Serializable
        internal data class User(
            @SerialName("creatorId")
            val creatorId: String? = null,
            @SerialName("iconUrl")
            val iconUrl: String? = null,
            @SerialName("isCreator")
            val isCreator: Boolean = false,
            @SerialName("isSupporter")
            val isSupporter: Boolean = false,
            @SerialName("name")
            val name: String = "",
            @SerialName("showAdultContent")
            val showAdultContent: Boolean = false,
            @SerialName("userId")
            val userId: String? = null,
        )
    }
}

@Serializable
internal data class FanboxPostIdRequest(
    @SerialName("postId")
    val postId: String,
)

@Serializable
internal data class FanboxFollowRequest(
    @SerialName("creatorUserId")
    val creatorUserId: String,
)

@Serializable
internal data class FanboxUserEntity(
    @SerialName("iconUrl")
    val iconUrl: String? = null,
    @SerialName("name")
    val name: String = "",
    @SerialName("userId")
    val userId: String = "",
)

@Serializable
internal data class FanboxCoverEntity(
    @SerialName("type")
    val type: String = "",
    @SerialName("url")
    val url: String = "",
)

@Serializable
internal data class FanboxPostEntity(
    @SerialName("commentCount")
    val commentCount: Int = 0,
    @SerialName("cover")
    val cover: FanboxCoverEntity? = null,
    @SerialName("creatorId")
    val creatorId: String = "",
    @SerialName("excerpt")
    val excerpt: String = "",
    @SerialName("feeRequired")
    val feeRequired: Int = 0,
    @SerialName("hasAdultContent")
    val hasAdultContent: Boolean = false,
    @SerialName("id")
    val id: String = "",
    @SerialName("isLiked")
    val isLiked: Boolean = false,
    @SerialName("isRestricted")
    val isRestricted: Boolean = false,
    @SerialName("likeCount")
    val likeCount: Int = 0,
    @SerialName("publishedDatetime")
    val publishedDatetime: String = "",
    @SerialName("tags")
    val tags: List<String> = emptyList(),
    @SerialName("title")
    val title: String = "",
    @SerialName("updatedDatetime")
    val updatedDatetime: String = "",
    @SerialName("user")
    val user: FanboxUserEntity? = null,
)

@Serializable
internal data class FanboxPostListResponse(
    @SerialName("body")
    val body: Body = Body(),
) {
    @Serializable
    internal data class Body(
        @SerialName("items")
        val items: List<FanboxPostEntity> = emptyList(),
        @SerialName("nextUrl")
        val nextUrl: String? = null,
    )
}

@Serializable
internal data class FanboxCreatorPostListResponse(
    @SerialName("body")
    val body: List<FanboxPostEntity> = emptyList(),
)

@Serializable
internal data class FanboxCreatorPostPagesResponse(
    @SerialName("body")
    val body: List<String> = emptyList(),
)

@Serializable
internal data class FanboxPostSearchResponse(
    @SerialName("body")
    val body: Body = Body(),
) {
    @Serializable
    internal data class Body(
        @SerialName("items")
        val items: List<FanboxPostEntity> = emptyList(),
        @SerialName("nextUrl")
        val nextUrl: String? = null,
    )
}

@Serializable
internal data class FanboxPostDetailResponse(
    @SerialName("body")
    @Serializable(with = FanboxPostDetailBodySerializer::class)
    val body: FanboxPostDetailBody = FanboxPostDetailBody(),
)

@OptIn(ExperimentalSerializationApi::class)
private object FanboxPostDetailBodySerializer :
    JsonTransformingSerializer<FanboxPostDetailBody>(FanboxPostDetailBody.serializer()) {
    override fun transformDeserialize(element: JsonElement): JsonElement = (element as? JsonObject)?.get("post") ?: element
}

@Serializable
internal data class FanboxPostDetailBody(
    @SerialName("body")
    val body: BodyContent? = null,
    @SerialName("commentCount")
    val commentCount: Int = 0,
    @SerialName("creatorId")
    val creatorId: String = "",
    @SerialName("excerpt")
    val excerpt: String = "",
    @SerialName("feeRequired")
    val feeRequired: Int = 0,
    @SerialName("hasAdultContent")
    val hasAdultContent: Boolean = false,
    @SerialName("id")
    val id: String = "",
    @SerialName("imageForShare")
    val imageForShare: String? = null,
    @SerialName("isLiked")
    val isLiked: Boolean = false,
    @SerialName("isRestricted")
    val isRestricted: Boolean = false,
    @SerialName("likeCount")
    val likeCount: Int = 0,
    @SerialName("publishedDatetime")
    val publishedDatetime: String = "",
    @SerialName("tags")
    val tags: List<String> = emptyList(),
    @SerialName("title")
    val title: String = "",
    @SerialName("type")
    val type: String = "",
    @SerialName("updatedDatetime")
    val updatedDatetime: String = "",
    @SerialName("coverImageUrl")
    val coverImageUrl: String? = null,
    @SerialName("user")
    val user: FanboxUserEntity? = null,
) {
    @Serializable
    internal data class BodyContent(
        @SerialName("text")
        val text: String? = null,
        @SerialName("blocks")
        val blocks: List<Block> = emptyList(),
        @SerialName("fileMap")
        val fileMap: Map<String, FileItem> = emptyMap(),
        @SerialName("imageMap")
        val imageMap: Map<String, ImageItem> = emptyMap(),
        @SerialName("urlEmbedMap")
        val urlEmbedMap: Map<String, UrlEmbed> = emptyMap(),
        @SerialName("images")
        val images: List<ImageItem> = emptyList(),
        @SerialName("files")
        val files: List<FileItem> = emptyList(),
    )

    @Serializable
    internal data class Block(
        @SerialName("type")
        val type: String = "",
        @SerialName("text")
        val text: String? = null,
        @SerialName("imageId")
        val imageId: String? = null,
        @SerialName("fileId")
        val fileId: String? = null,
        @SerialName("urlEmbedId")
        val urlEmbedId: String? = null,
    )

    @Serializable
    internal data class FileItem(
        @SerialName("extension")
        val extension: String = "",
        @SerialName("id")
        val id: String = "",
        @SerialName("name")
        val name: String = "",
        @SerialName("size")
        val size: Long = 0,
        @SerialName("url")
        val url: String = "",
    )

    @Serializable
    internal data class ImageItem(
        @SerialName("extension")
        val extension: String = "",
        @SerialName("height")
        val height: Int = 0,
        @SerialName("id")
        val id: String = "",
        @SerialName("originalUrl")
        val originalUrl: String = "",
        @SerialName("thumbnailUrl")
        val thumbnailUrl: String = "",
        @SerialName("width")
        val width: Int = 0,
    )

    @Serializable
    internal data class UrlEmbed(
        @SerialName("id")
        val id: String = "",
        @SerialName("type")
        val type: String = "",
        @SerialName("html")
        val html: String? = null,
        @SerialName("postInfo")
        val postInfo: FanboxPostEntity? = null,
    )
}

@Serializable
internal data class FanboxCreatorDetailResponse(
    @SerialName("body")
    val body: FanboxCreatorDetailBody = FanboxCreatorDetailBody(),
)

@Serializable
internal data class FanboxCreatorDetailBody(
    @SerialName("coverImageUrl")
    val coverImageUrl: String? = null,
    @SerialName("creatorId")
    val creatorId: String = "",
    @SerialName("description")
    val description: String = "",
    @SerialName("hasAdultContent")
    val hasAdultContent: Boolean = false,
    @SerialName("isFollowed")
    val isFollowed: Boolean = false,
    @SerialName("isSupported")
    val isSupported: Boolean = false,
    @SerialName("profileItems")
    val profileItems: List<ProfileItem> = emptyList(),
    @SerialName("profileLinks")
    val profileLinks: List<String> = emptyList(),
    @SerialName("user")
    val user: FanboxUserEntity? = null,
) {
    @Serializable
    internal data class ProfileItem(
        @SerialName("id")
        val id: String = "",
        @SerialName("imageUrl")
        val imageUrl: String? = null,
        @SerialName("thumbnailUrl")
        val thumbnailUrl: String? = null,
        @SerialName("type")
        val type: String = "",
    )
}

@Serializable
internal data class FanboxCreatorListResponse(
    @SerialName("body")
    val body: Body = Body(),
) {
    @Serializable
    internal data class Body(
        @SerialName("creators")
        val creators: List<FanboxCreatorDetailBody> = emptyList(),
    )
}

@Serializable
internal data class FanboxCreatorSearchResponse(
    @SerialName("body")
    val body: Body = Body(),
) {
    @Serializable
    internal data class Body(
        @SerialName("creators")
        val creators: List<FanboxCreatorDetailBody> = emptyList(),
        @SerialName("nextPage")
        val nextPage: Int? = null,
    )
}

@Serializable
internal data class FanboxCommentListResponse(
    @SerialName("body")
    val body: Body = Body(),
) {
    @Serializable
    internal data class Body(
        @SerialName("commentList")
        val commentList: CommentList = CommentList(),
    )

    @Serializable
    internal data class CommentList(
        @SerialName("items")
        val items: List<FanboxCommentItem> = emptyList(),
        @SerialName("nextUrl")
        val nextUrl: String? = null,
    )
}

@Serializable
internal data class FanboxCommentItem(
    @SerialName("body")
    val body: String = "",
    @SerialName("createdDatetime")
    val createdDatetime: String = "",
    @SerialName("id")
    val id: String = "",
    @SerialName("isLiked")
    val isLiked: Boolean = false,
    @SerialName("likeCount")
    val likeCount: Int = 0,
    @SerialName("parentCommentId")
    val parentCommentId: String = "",
    @SerialName("rootCommentId")
    val rootCommentId: String = "",
    @SerialName("user")
    val user: FanboxUserEntity? = null,
    @SerialName("replies")
    val replies: List<FanboxCommentItem> = emptyList(),
)

internal data class FanboxPostPage(
    val items: List<FanboxPostEntity>,
    val nextKey: String?,
)

internal data class FanboxCreatorPage(
    val items: List<FanboxCreatorDetailBody>,
    val nextKey: String?,
)

internal data class FanboxCursor(
    val firstPublishedDatetime: String?,
    val maxPublishedDatetime: String?,
    val firstId: String?,
    val maxId: String?,
    val limit: Int?,
)

internal fun String.toFanboxCursor(): FanboxCursor {
    val parameters = Url(this).parameters
    return FanboxCursor(
        firstPublishedDatetime = parameters["firstPublishedDatetime"],
        maxPublishedDatetime = parameters["maxPublishedDatetime"],
        firstId = parameters["firstId"],
        maxId = parameters["maxId"],
        limit = parameters["limit"]?.toIntOrNull(),
    )
}
