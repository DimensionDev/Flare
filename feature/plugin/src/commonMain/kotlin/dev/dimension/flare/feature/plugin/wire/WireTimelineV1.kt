package dev.dimension.flare.feature.plugin.wire

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public sealed interface TimelineItemV1 {
    @Serializable
    @SerialName("post")
    public data class Post(
        val post: PostV1,
        val presentation: PostPresentationV1 = PostPresentationV1(),
    ) : TimelineItemV1

    @Serializable
    @SerialName("feed")
    public data class Feed(
        val feed: FeedV1,
    ) : TimelineItemV1

    @Serializable
    @SerialName("user")
    public data class User(
        val user: TimelineUserV1,
    ) : TimelineItemV1

    @Serializable
    @SerialName("userList")
    public data class UserList(
        val userList: TimelineUserListV1,
    ) : TimelineItemV1

    @Serializable
    @SerialName("message")
    public data class Message(
        val message: MessageV1,
    ) : TimelineItemV1
}

@Serializable
public data class PostPresentationV1(
    val message: MessageV1? = null,
    val inlineParents: List<TimelineItemV1.Post> = emptyList(),
    val quotes: List<PostV1> = emptyList(),
    val repost: PostV1? = null,
    val notificationKey: EntityKeyV1? = null,
)

@Serializable
public sealed interface NavigationTargetV1 {
    @Serializable
    @SerialName("post")
    public data class Post(
        val key: EntityKeyV1,
    ) : NavigationTargetV1

    @Serializable
    @SerialName("profile")
    public data class Profile(
        val key: EntityKeyV1,
    ) : NavigationTargetV1

    @Serializable
    @SerialName("article")
    public data class Article(
        val key: EntityKeyV1,
    ) : NavigationTargetV1

    @Serializable
    @SerialName("gallery")
    public data class Gallery(
        val key: EntityKeyV1,
    ) : NavigationTargetV1

    @Serializable
    @SerialName("link")
    public data class Link(
        val url: String,
    ) : NavigationTargetV1
}

@Serializable
public data class FeedV1(
    val url: String,
    val createdAt: String,
    val source: FeedSourceV1,
    val title: String? = null,
    val description: RichTextV1? = null,
    val media: MediaV1? = null,
    val sourceLanguages: List<String> = emptyList(),
    val target: NavigationTargetV1? = null,
)

@Serializable
public data class FeedSourceV1(
    val name: String,
    val iconUrl: String? = null,
)

@Serializable
public data class TimelineUserV1(
    val key: EntityKeyV1,
    val createdAt: String,
    val profile: ProfileV1,
    val message: MessageV1? = null,
    val buttons: List<ActionDescriptorV1> = emptyList(),
)

@Serializable
public data class TimelineUserListV1(
    val key: EntityKeyV1,
    val createdAt: String,
    val users: List<ProfileV1>,
    val message: MessageV1? = null,
    val post: PostV1? = null,
)

@Serializable
public data class MessageV1(
    val key: EntityKeyV1,
    val createdAt: String,
    val icon: HostIconV1 = HostIconV1.Info,
    val actor: ProfileV1? = null,
    val text: String? = null,
    val kind: MessageKindV1? = null,
    val unknownType: String? = null,
    val args: List<String> = emptyList(),
    val target: NavigationTargetV1? = null,
)

@Serializable
public enum class MessageKindV1 {
    Mention,
    NewPost,
    Repost,
    Follow,
    FollowRequest,
    Favourite,
    PollEnded,
    PostUpdated,
    Reply,
    Quote,
    Reaction,
    FollowRequestAccepted,
    ScheduledNotePosted,
    ScheduledNotePostFailed,
    RoleAssigned,
    ChatRoomInvitationReceived,
    AchievementEarned,
    ExportCompleted,
    Test,
    Login,
    CreateToken,
    App,
    StarterpackJoined,
    Pinned,
    Like,
}

@Serializable
public enum class PostReferenceTypeV1 { Reply, Quote, Repost }

@Serializable
public data class PostReferenceV1(
    val key: EntityKeyV1,
    val type: PostReferenceTypeV1,
)

@Serializable
public data class SourceChannelV1(
    val id: String,
    val name: String,
)

@Serializable
public data class CardV1(
    val title: String,
    val url: String,
    val description: String? = null,
    val media: MediaV1? = null,
)

@Serializable
public data class PollV1(
    val id: String,
    val options: List<PollOptionV1>,
    val multiple: Boolean = false,
    val ownVotes: List<Int> = emptyList(),
    val expiresAt: String? = null,
    val enabled: Boolean = true,
    val actionToken: String? = null,
)

@Serializable
public data class PollOptionV1(
    val title: String,
    val votesCount: Long = 0,
)

@Serializable
public data class EmojiReactionV1(
    val name: String,
    val count: Long = 0,
    val url: String? = null,
    val isUnicode: Boolean = true,
    val me: Boolean = false,
    val enabled: Boolean = true,
    val actionToken: String? = null,
)

@Serializable
public data class UgoiraMetadataV1(
    val zipUrl: String,
    val frames: List<UgoiraFrameV1>,
)

@Serializable
public data class UgoiraFrameV1(
    val file: String,
    val delayMillis: Int,
)
