package dev.dimension.flare.data.network.xqt.model.legacy

import dev.dimension.flare.data.network.mastodon.api.model.Trend
import dev.dimension.flare.data.network.xqt.model.CursorType
import dev.dimension.flare.data.network.xqt.model.TweetLegacy
import dev.dimension.flare.data.network.xqt.model.UserLegacy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
data class TopLevel(
    val globalObjects: GlobalObjects? = null,
    val timeline: Timeline? = null
) {
    @Serializable
    data class Timeline(
        val id: String? = null,
        val instructions: List<Instruction>? = null
    )

    @Serializable
    data class Instruction(
        val addEntries: AddEntries? = null,
    )

    @Serializable
    data class AddEntries(
        val entries: List<Entry>? = null
    )

    @Serializable
    data class Entry(
        @SerialName("entryId")
        val entryID: String? = null,

        val sortIndex: String? = null,
        val content: EntryContent? = null
    )

    @Serializable
    data class EntryContent(
        val operation: Operation? = null,
        val item: Item? = null
    )

    @Serializable
    data class Item(
        val content: ItemContent? = null,
        val clientEventInfo: ClientEventInfo? = null
    )

    @Serializable
    data class ClientEventInfo(
        val component: String? = null,
        val element: String? = null,
//        val details: Details? = null
    )


    @Serializable
    data class ItemContent(
        val tweet: ContentTweet? = null,
        val trend: Trend? = null
    )

    @Serializable
    data class ContentTweet(
        val id: String? = null,
        val displayType: String? = null
    )

    @Serializable
    data class Operation(
        val cursor: Cursor? = null
    )

    @Serializable
    data class Cursor(
        val value: String? = null,
        val cursorType: CursorType? = null
    )

}

@Serializable
data class GlobalObjects(
    val users: Map<String, UserLegacy>? = null,
    val tweets: Map<String, TweetLegacy>? = null
)
