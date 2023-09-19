package dev.dimension.flare.data.network.misskey.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Visibility {
    @SerialName("public")
    Public,

    @SerialName("home")
    Home,

    @SerialName("followers")
    Followers,

    @SerialName("specified")
    Specified,
}
