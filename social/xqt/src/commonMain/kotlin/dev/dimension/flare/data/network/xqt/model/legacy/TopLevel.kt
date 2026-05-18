package dev.dimension.flare.data.network.xqt.model.legacy

import dev.dimension.flare.data.network.xqt.model.CursorType
import dev.dimension.flare.data.network.xqt.model.TweetLegacy
import dev.dimension.flare.data.network.xqt.model.UserLegacy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public data class TopLevel(
    val globalObjects: GlobalObjects? = null,
    val timeline: Timeline? = null,
) {
    @Serializable
    public data class Timeline(
        val id: String? = null,
        val instructions: List<Instruction>? = null,
    )

    @Serializable
    public data class Instruction(
        val addEntries: AddEntries? = null,
    )

    @Serializable
    public data class AddEntries(
        val entries: List<Entry>? = null,
    )

    @Serializable
    public data class Entry(
        @SerialName("entryId")
        val entryID: String? = null,
        val sortIndex: String? = null,
        val content: EntryContent? = null,
    )

    @Serializable
    public data class EntryContent(
        val operation: Operation? = null,
        val item: Item? = null,
        val timelineModule: TimelineModule? = null,
    )

    @Serializable
    public data class TimelineModule(
        val items: List<ItemElement>? = null,
        val displayType: String? = null,
//        val header: Header? = null,
//        val clientEventInfo: TimelineModuleClientEventInfo? = null
    )

    @Serializable
    public data class ItemElement(
        @SerialName("entryId")
        val entryID: String? = null,
        val item: Item? = null,
    )

    @Serializable
    public data class Item(
        val content: ItemContent? = null,
        val clientEventInfo: ClientEventInfo? = null,
    )

    @Serializable
    public data class ClientEventInfo(
        val component: String? = null,
        val element: String? = null,
//        val details: Details? = null
    )

    @Serializable
    public data class ItemContent(
        val tweet: ContentTweet? = null,
        val trend: Trend? = null,
        val notification: NotificationClass? = null,
    )

    @Serializable
    public data class NotificationClass(
        val id: String? = null,
        val url: NotificationURL? = null,
        val fromUsers: List<String>? = null,
        val targetTweets: List<String>? = null,
    )

    @Serializable
    public data class NotificationURL(
        val urlType: String? = null,
        val url: String? = null,
    )

    @Serializable
    public data class Trend(
        val name: String? = null,
//        val url: String? = null,
//        val promotedMetadata: PromotedMetadata? = null,
        val description: String? = null,
        val metaDescription: String? = null,
//        val associatedCardUrls: JsonArray? = null,
//        val trendMetadata: TrendTrendMetadata? = null,
        val rank: String? = null,
    )

    @Serializable
    public data class ContentTweet(
        val id: String? = null,
        val displayType: String? = null,
    )

    @Serializable
    public data class Operation(
        val cursor: Cursor? = null,
    )

    @Serializable
    public data class Cursor(
        public val value: String? = null,
        val cursorType: CursorType? = null,
    )
}

@Serializable
public data class GlobalObjects(
    val users: Map<String, UserLegacy>? = null,
    val tweets: Map<String, TweetLegacy>? = null,
    val notifications: Map<String, Notification>? = null,
)

@Serializable
public data class Notification(
    val id: String? = null,
    @SerialName("timestampMs")
    val timestampMS: String? = null,
    val icon: Icon? = null,
    val message: Message? = null,
    val template: Template? = null,
)

@Serializable
public data class Icon(
    val id: String? = null,
)

@Serializable
public data class Message(
    val text: String? = null,
    val entities: List<Entity>? = null,
    val rtl: Boolean? = null,
)

@Serializable
public data class Entity(
    val fromIndex: Long? = null,
    val toIndex: Long? = null,
    val ref: Ref? = null,
    val format: String? = null,
)

@Serializable
public data class Ref(
    val user: Icon? = null,
)

@Serializable
public data class Template(
    val aggregateUserActionsV1: AggregateUserActionsV1? = null,
)

@Serializable
public data class AggregateUserActionsV1(
    val targetObjects: List<TargetObject>? = null,
    val fromUsers: List<Ref>? = null,
    val additionalContext: AdditionalContext? = null,
)

@Serializable
public data class AdditionalContext(
    val contextText: ContextText? = null,
)

@Serializable
public data class ContextText(
    val text: String? = null,
    val entities: List<Entity>? = null,
)

@Serializable
public data class TargetObject(
    val tweet: Icon? = null,
)
