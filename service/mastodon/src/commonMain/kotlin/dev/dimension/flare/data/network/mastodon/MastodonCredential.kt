package dev.dimension.flare.data.network.mastodon

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("MastodonCredential")
internal data class MastodonCredential(
    val instance: String,
    val accessToken: String,
    val forkType: ForkType = ForkType.Mastodon,
    val nodeType: String? = null,
) {
    public enum class ForkType {
        Mastodon,
        Pleroma,
    }
}
