package dev.dimension.flare.data.database.cache.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import app.bsky.actor.ProfileViewBasic
import app.bsky.actor.ProfileViewDetailed
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
        val data: dev.dimension.flare.data.network.misskey.api.model.User
    ) : UserContent

    @Serializable
    @SerialName("MisskeyLite")
    data class MisskeyLite(
        val data: dev.dimension.flare.data.network.misskey.api.model.UserLite
    ) : UserContent

    @Serializable
    @SerialName("Bluesky")
    data class Bluesky(
        val data: ProfileViewDetailed
    ) : UserContent

    @Serializable
    @SerialName("BlueskyLite")
    data class BlueskyLite(
        val data: ProfileViewBasic
    ) : UserContent
}
