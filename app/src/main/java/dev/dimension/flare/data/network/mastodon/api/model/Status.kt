package dev.dimension.flare.data.network.mastodon.api.model

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Status(
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
)
