package dev.dimension.flare.data.network.misskey.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal enum class Visibility {
    @SerialName("public")
    Public,

    @SerialName("home")
    Home,

    @SerialName("followers")
    Followers,

    @SerialName("specified")
    Specified,
}
