package dev.dimension.flare.data.database.cache.model

import app.bsky.actor.ProfileViewBasic
import app.bsky.actor.ProfileViewDetailed
import dev.dimension.flare.data.network.xqt.model.User
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// https://github.com/cashapp/sqldelight/issues/1333
@Serializable
internal sealed interface UserContent {
    @Serializable
    @SerialName("Mastodon")
    data class Mastodon internal constructor(
        internal val data: dev.dimension.flare.data.network.mastodon.api.model.Account,
    ) : UserContent

    @Serializable
    @SerialName("Misskey")
    data class Misskey internal constructor(
        internal val data: dev.dimension.flare.data.network.misskey.api.model.User,
    ) : UserContent

    @Serializable
    @SerialName("MisskeyLite")
    data class MisskeyLite internal constructor(
        internal val data: dev.dimension.flare.data.network.misskey.api.model.UserLite,
    ) : UserContent

    @Serializable
    @SerialName("Bluesky")
    data class Bluesky(
        val data: ProfileViewDetailed,
    ) : UserContent

    @Serializable
    @SerialName("BlueskyLite")
    data class BlueskyLite(
        val data: ProfileViewBasic,
    ) : UserContent

    @Serializable
    @SerialName("XQT")
    data class XQT internal constructor(
        internal val data: User,
    ) : UserContent

    @Serializable
    @SerialName("VVO")
    data class VVO internal constructor(
        internal val data: dev.dimension.flare.data.network.vvo.model.User,
    ) : UserContent
}
