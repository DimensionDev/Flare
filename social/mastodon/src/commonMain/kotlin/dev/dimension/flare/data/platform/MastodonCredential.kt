package dev.dimension.flare.data.platform

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Immutable
@Serializable
@SerialName("MastodonCredential")
internal data class MastodonCredential(
    val instance: String,
    val accessToken: String,
    val forkType: ForkType = ForkType.Mastodon,
    // to support more forks in the future
    val nodeType: String? = null,
) {
    enum class ForkType {
        Mastodon,
        Pleroma,
    }
}
