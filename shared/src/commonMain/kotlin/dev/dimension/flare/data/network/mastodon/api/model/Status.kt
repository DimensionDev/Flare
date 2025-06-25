package dev.dimension.flare.data.network.mastodon.api.model

import dev.dimension.flare.common.JSON
import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
internal data class Status(
    val id: String? = null,
    @SerialName("created_at")
    @Serializable(with = DateSerializer::class)
    val createdAt: Instant? = null,
    @SerialName("in_reply_to_id")
    val inReplyToID: String? = null,
    @SerialName("in_reply_to_account_id")
    val inReplyToAccountID: String? = null,
    val sensitive: Boolean? = null,
    @SerialName("spoiler_text")
    val spoilerText: String? = null,
    val visibility: Visibility? = null,
    val language: String? = null,
    val uri: String? = null,
    val url: String? = null,
    @SerialName("replies_count")
    val repliesCount: Long? = null,
    @SerialName("reblogs_count")
    val reblogsCount: Long? = null,
    @SerialName("favourites_count")
    val favouritesCount: Long? = null,
    val favourited: Boolean? = null,
    val reblogged: Boolean? = null,
    val muted: Boolean? = null,
    val bookmarked: Boolean? = null,
    val content: String? = null,
    val reblog: Status? = null,
    val application: Application? = null,
    val account: Account? = null,
    @SerialName("media_attachments")
    val mediaAttachments: List<Attachment>? = null,
    val mentions: List<Mention>? = null,
    val tags: List<Tag>? = null,
    val emojis: List<Emoji>? = null,
    val card: Card? = null,
    val poll: Poll? = null,
    val pinned: Boolean? = null,
    // compatibility layer for Pleroma/Akkoma
    @SerialName("emoji_reactions")
    val emojiReactions: List<EmojiReaction>? = null,
    // compatibility layer for Pleroma/Akkoma
    @SerialName("quotes_count")
    val quotesCount: Long? = null,
    @SerialName("quote")
    val json_quote: JsonObject? = null,
) {
    val quote: Status?
        get() =
            json_quote?.let {
                if (it.containsKey("state")) {
                    // Mastodon quote
                    JSON
                        .decodeFromJsonElement(
                            MastodonQuote.serializer(),
                            it,
                        ).quoted_status
                } else {
                    // Pleroma/Akkoma quote
                    JSON.decodeFromJsonElement(Status.serializer(), it)
                }
            }
}

@Serializable
internal data class MastodonQuote(
    val state: String? = null,
    @SerialName("quoted_status")
    val quoted_status: Status? = null,
)
