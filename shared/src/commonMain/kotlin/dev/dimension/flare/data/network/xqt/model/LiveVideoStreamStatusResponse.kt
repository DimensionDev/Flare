package dev.dimension.flare.data.network.xqt.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class LiveVideoStreamStatusResponse(
    val source: Source? = null,
    @SerialName("sessionId")
    val sessionID: String? = null,
    val chatToken: String? = null,
    val lifecycleToken: String? = null,
    @SerialName("shareUrl")
    val shareURL: String? = null,
    val chatPermissionType: String? = null,
)

@Serializable
internal data class Source(
    val location: String? = null,
    @SerialName("noRedirectPlaybackUrl")
    val noRedirectPlaybackURL: String? = null,
    val status: String? = null,
    val streamType: String? = null,
)
