package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
@Immutable
public sealed class UiList {
//    public abstract val id: String
    public abstract val id: String
    public abstract val title: String
    public abstract val readonly: Boolean

    @Serializable
    @Immutable
    public data class List(
        override val id: String,
        override val title: String,
        val description: String? = null,
        val avatar: String? = null,
        val creator: UiUserV2? = null,
        override val readonly: Boolean = false,
    ) : UiList()

    @Serializable
    @Immutable
    public data class Feed(
        override val id: String,
        override val title: String,
        val description: String? = null,
        val avatar: String? = null,
        val creator: UiUserV2? = null,
        val likedCount: UiNumber = UiNumber(0),
        val liked: Boolean = false,
        override val readonly: Boolean = false,
    ) : UiList()

    @Serializable
    @Immutable
    public data class Antenna(
        override val id: String,
        override val title: String,
        override val readonly: Boolean = false,
    ) : UiList()

    @Serializable
    @Immutable
    public data class Channel(
        override val id: String,
        override val title: String,
        val isArchived: Boolean,
        val notesCount: Double,
        val usersCount: Double,
        val description: String? = null,
        val banner: String? = null,
        val isFollowing: Boolean? = null,
        val isFavorited: Boolean? = null,
        override val readonly: Boolean = false,
    ) : UiList()
}
