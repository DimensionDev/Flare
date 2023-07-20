package dev.dimension.flare.data.network.mastodon.api.model

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Notification(
  val id: String? = null,
  val type: NotificationTypes? = null,

  @SerialName("created_at")
  @Serializable(with = DateSerializer::class)
  val createdAt: Instant? = null,

  val account: Account? = null,
  val status: Status? = null
)

@Serializable
enum class NotificationTypes {
  @SerialName("follow")
  follow,

  @SerialName("favourite")
  favourite,

  @SerialName("reblog")
  reblog,

  @SerialName("mention")
  mention,

  @SerialName("poll")
  poll,

  @SerialName("follow_request")
  follow_request,

  @SerialName("status")
  status,

  @SerialName("update")
  update,
}
