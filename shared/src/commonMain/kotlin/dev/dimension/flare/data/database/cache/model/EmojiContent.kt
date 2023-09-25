package dev.dimension.flare.data.database.cache.model

import dev.dimension.flare.data.network.mastodon.api.model.Emoji
import dev.dimension.flare.data.network.misskey.api.model.EmojiSimple
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface EmojiContent {
    @Serializable
    @SerialName("Mastodon")
    data class Mastodon(val data: List<Emoji>) : EmojiContent

    @Serializable
    @SerialName("Misskey")
    data class Misskey(val data: List<EmojiSimple>) : EmojiContent
}
