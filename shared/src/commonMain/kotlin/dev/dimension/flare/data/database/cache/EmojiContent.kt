package dev.dimension.flare.data.database.cache

import kotlinx.serialization.Serializable

@Serializable
sealed interface EmojiContent {
//    @Serializable
//    @SerialName("Mastodon")
//    data class Mastodon(val data: List<Emoji>) : EmojiContent
//
//    @Serializable
//    @SerialName("Misskey")
//    data class Misskey(val data: List<EmojiSimple>) : EmojiContent
}