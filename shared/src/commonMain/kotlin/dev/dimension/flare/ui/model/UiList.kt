package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.serialization.Serializable

@Serializable
@Immutable
public sealed class UiList {
//    public abstract val id: String
    public abstract val key: MicroBlogKey
    public abstract val title: String

    @Serializable
    @Immutable
    public data class List(
        override val key: MicroBlogKey,
        override val title: String,
        val description: String? = null,
        val avatar: String? = null,
        val creator: UiUserV2? = null,
        val readonly: Boolean = false,
    ) : UiList()

    @Serializable
    @Immutable
    public data class Feed(
        override val key: MicroBlogKey,
        override val title: String,
        val description: String? = null,
        val avatar: String? = null,
        val creator: UiUserV2? = null,
        val likedCount: UiNumber = UiNumber(0),
        val liked: Boolean = false,
    ) : UiList()

    @Serializable
    @Immutable
    public data class Antenna(
        override val key: MicroBlogKey,
        override val title: String,
    ) : UiList()

    @Serializable
    @Immutable
    public data class Channel(
        override val key: MicroBlogKey,
        override val title: String,
        val isArchived: Boolean,
        val notesCount: Double,
        val usersCount: Double,
        val description: String? = null,
        val banner: String? = null,
        val isFollowing: Boolean? = null,
        val isFavorited: Boolean? = null,
    ) : UiList()
}
