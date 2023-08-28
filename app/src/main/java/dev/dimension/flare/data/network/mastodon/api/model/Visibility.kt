package dev.dimension.flare.data.network.mastodon.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Visibility {
    @SerialName("public")
    Public,

    @SerialName("unlisted")
    Unlisted,

    @SerialName("private")
    Private,

    @SerialName("direct")
    Direct,
}
