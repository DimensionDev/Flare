package dev.dimension.flare.data.model

import androidx.compose.runtime.Immutable
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiIcon
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public sealed class IconType {
    @Immutable
    @Serializable
    public data class Avatar(
        @SerialName("userKey")
        val accountKey: MicroBlogKey,
    ) : IconType()

    @Immutable
    @Serializable
    public data class Url(
        val url: String,
    ) : IconType()

    @Immutable
    @Serializable
    public data class FavIcon(
        val host: String,
    ) : IconType()

    @Immutable
    @Serializable
    public data class Material(
        val icon: UiIcon,
    ) : IconType()

    @Immutable
    @Serializable
    public data class Mixed(
        val icon: UiIcon,
        @SerialName("userKey")
        val accountKey: MicroBlogKey,
    ) : IconType()
}