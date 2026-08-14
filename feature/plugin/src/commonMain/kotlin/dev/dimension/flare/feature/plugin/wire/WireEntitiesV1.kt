package dev.dimension.flare.feature.plugin.wire

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public data class RelationV1(
    val profileKey: EntityKeyV1,
    val following: Boolean = false,
    val followedBy: Boolean = false,
    val blocking: Boolean = false,
    val muting: Boolean = false,
    val actionTokens: Map<SemanticActionV1, String> = emptyMap(),
)

@Serializable
public enum class NotificationKindV1 {
    Mention,
    Reply,
    Favourite,
    Repost,
    Follow,
    Other,
}

@Serializable
public data class NotificationV1(
    val id: String,
    val createdAt: String,
    val kind: NotificationKindV1,
    val actor: ProfileV1? = null,
    val post: PostV1? = null,
    val message: RichTextV1? = null,
)

@Serializable
public data class SocialListV1(
    val id: String,
    val title: String,
    val memberCount: Int? = null,
    val entityToken: String? = null,
)

@Serializable
public data class DirectMessageRoomV1(
    val key: EntityKeyV1,
    val title: String,
    val participants: List<ProfileV1>,
    val lastMessage: DirectMessageV1? = null,
    val unreadCount: Int = 0,
    val entityToken: String? = null,
)

@Serializable
public data class DirectMessageV1(
    val key: EntityKeyV1,
    val roomKey: EntityKeyV1,
    val sender: ProfileV1,
    val createdAt: String,
    val content: RichTextV1,
    val fromCurrentAccount: Boolean = false,
    val entityToken: String? = null,
)

@Serializable
public data class ArticleV1(
    val key: EntityKeyV1,
    val title: String,
    val author: ProfileV1? = null,
    val createdAt: String? = null,
    val content: RichTextV1,
    val url: String? = null,
    val coverUrl: String? = null,
)

@Serializable
public enum class GalleryOrientationV1 {
    @SerialName("horizontal")
    Horizontal,

    @SerialName("vertical")
    Vertical,
}

@Serializable
public data class GalleryV1(
    val key: EntityKeyV1,
    val title: String,
    val author: ProfileV1? = null,
    val createdAt: String,
    val content: RichTextV1? = null,
    val url: String,
    val images: List<MediaV1>,
    val orientation: GalleryOrientationV1 = GalleryOrientationV1.Vertical,
    val entityToken: String? = null,
    val actions: List<ActionDescriptorV1> = emptyList(),
)

@Serializable
public data class TimelineDescriptorV1(
    val id: String,
    val title: WireTextV1,
    val icon: HostIconV1,
    val display: TimelineDisplayV1 = TimelineDisplayV1.List,
    val parameters: Map<String, String> = emptyMap(),
)

@Serializable
public data class TimelineSectionV1(
    val id: String,
    val title: WireTextV1,
    val timelines: PageV1<TimelineDescriptorV1>,
)
