package dev.dimension.flare.data.model

import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiIcon
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public sealed class IconType {
    @Serializable
    public data class Avatar(
        @SerialName("userKey")
        val accountKey: MicroBlogKey,
    ) : IconType()

    @Serializable
    public data class Url(
        val url: String,
    ) : IconType()

    @Serializable
    public data class FavIcon(
        val host: String,
    ) : IconType()

    @Serializable
    public data class Material(
        val icon: UiIcon,
    ) : IconType()

    @Serializable
    public data class Mixed(
        val icon: UiIcon,
        @SerialName("userKey")
        val accountKey: MicroBlogKey,
    ) : IconType()
}
