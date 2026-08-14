package dev.dimension.flare.feature.plugin.wire

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public data class EntityKeyV1(
    val id: String,
    val host: String,
)

@Serializable
public enum class RichTextFormatV1 {
    @SerialName("plain")
    Plain,

    @SerialName("html")
    Html,
}

@Serializable
public data class RichTextV1(
    val format: RichTextFormatV1 = RichTextFormatV1.Plain,
    val value: String,
)

@Serializable
public data class ProfileV1(
    val key: EntityKeyV1,
    val handle: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val bannerUrl: String? = null,
    val description: RichTextV1? = null,
    val url: String? = null,
    val followersCount: Long? = null,
    val followingCount: Long? = null,
    val postsCount: Long? = null,
    val locked: Boolean = false,
    val bot: Boolean = false,
    val fields: List<ProfileFieldV1> = emptyList(),
    val entityToken: String? = null,
    val actions: List<ActionDescriptorV1> = emptyList(),
)

@Serializable
public data class ProfileFieldV1(
    val name: String,
    val value: RichTextV1,
)

@Serializable
public enum class MediaTypeV1 {
    @SerialName("image")
    Image,

    @SerialName("video")
    Video,

    @SerialName("gif")
    Gif,

    @SerialName("audio")
    Audio,
}

@Serializable
public data class MediaV1(
    val id: String,
    val type: MediaTypeV1,
    val url: String,
    val previewUrl: String? = null,
    val description: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val durationMillis: Long? = null,
)

@Serializable
public enum class VisibilityV1 {
    @SerialName("public")
    Public,

    @SerialName("unlisted")
    Unlisted,

    @SerialName("followers")
    Followers,

    @SerialName("direct")
    Direct,
}

@Serializable
public data class PostV1(
    val key: EntityKeyV1,
    val author: ProfileV1,
    val createdAt: String,
    val content: RichTextV1,
    val url: String? = null,
    val media: List<MediaV1> = emptyList(),
    val repost: PostV1? = null,
    val replyTo: EntityKeyV1? = null,
    val spoilerText: String? = null,
    val sensitive: Boolean = false,
    val visibility: VisibilityV1 = VisibilityV1.Public,
    val favouritesCount: Long? = null,
    val repostsCount: Long? = null,
    val repliesCount: Long? = null,
    val entityToken: String? = null,
    val actions: List<ActionDescriptorV1> = emptyList(),
)

@Serializable
public data class HashtagV1(
    val name: String,
    val url: String? = null,
)

@Serializable
public enum class SemanticActionV1 {
    Favourite,
    Unfavourite,
    Repost,
    Unrepost,
    Bookmark,
    Unbookmark,
    Delete,
    Reply,
    Follow,
    Unfollow,
    Block,
    Unblock,
    Mute,
    Unmute,
}

@Serializable
public data class ActionDescriptorV1(
    val action: SemanticActionV1,
    val enabled: Boolean = true,
    val active: Boolean? = null,
    val count: Long? = null,
    val actionToken: String? = null,
)

@Serializable
public enum class PageDirectionV1 {
    @SerialName("refresh")
    Refresh,

    @SerialName("older")
    Older,

    @SerialName("newer")
    Newer,
}

@Serializable
public data class PageRequestV1(
    val direction: PageDirectionV1,
    val limit: Int,
    val cursor: String? = null,
    val parameters: Map<String, String> = emptyMap(),
)

@Serializable
public data class PageV1<T>(
    val items: List<T> = emptyList(),
    val olderCursor: String? = null,
    val newerCursor: String? = null,
    val endReached: Boolean = false,
)

@Serializable
public enum class HostIconV1 {
    Home,
    Notification,
    Search,
    Profile,
    Local,
    World,
    Featured,
    Bookmark,
    Heart,
    List,
    Messages,
    Channel,
    Like,
    Repost,
    Reply,
    Delete,
    Follow,
    Block,
    Mute,
    Info,
}

@Serializable
public enum class TimelineDisplayV1 {
    List,
    Grid,
}
