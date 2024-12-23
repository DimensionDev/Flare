package dev.dimension.flare.data.database.cache.model

import dev.dimension.flare.data.network.mastodon.api.model.Emoji
import dev.dimension.flare.data.network.misskey.api.model.EmojiSimple
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// https://github.com/cashapp/sqldelight/issues/1333
@Serializable
internal sealed interface EmojiContent {
    @Serializable
    @SerialName("Mastodon")
    data class Mastodon internal constructor(
        internal val data: List<Emoji>,
    ) : EmojiContent

    @Serializable
    @SerialName("Misskey")
    data class Misskey internal constructor(
        internal val data: List<EmojiSimple>,
    ) : EmojiContent
}
