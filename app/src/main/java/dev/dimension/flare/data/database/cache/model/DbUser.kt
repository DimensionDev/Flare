package dev.dimension.flare.data.database.cache.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import dev.dimension.flare.data.network.mastodon.api.model.Account
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Entity(
    tableName = "user"
)
data class DbUser(
    @PrimaryKey
    val userKey: MicroBlogKey,
    val platformType: PlatformType,
    val name: String,
    val handle: String,
    val host: String,
    val content: UserContent
)

@Serializable
sealed interface UserContent {
    @Serializable
    @SerialName("Mastodon")
    data class Mastodon(val data: Account) : UserContent

    @Serializable
    @SerialName("Misskey")
    data class Misskey(
        val data: dev.dimension.flare.data.network.misskey.api.model.User,
        val emojis: List<dev.dimension.flare.data.network.misskey.api.model.EmojiSimple>,
    ) : UserContent

    @Serializable
    @SerialName("MisskeyLite")
    data class MisskeyLite(
        val data: dev.dimension.flare.data.network.misskey.api.model.UserLite,
        val emojis: List<dev.dimension.flare.data.network.misskey.api.model.EmojiSimple>,
    ) : UserContent
}
