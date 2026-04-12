package dev.dimension.flare.data.network.mastodon.api.model

import kotlinx.serialization.Serializable

// compatibility layer for Pleroma/Akkoma
@Serializable
internal data class EmojiReaction(
    val name: String? = null,
    val count: Long? = null,
    val me: Boolean? = null,
    val url: String? = null,
)
