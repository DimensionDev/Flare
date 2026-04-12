package dev.dimension.flare.data.network.mastodon.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class Meta(
    val length: String? = null,
    val duration: Double? = null,
    val fps: Long? = null,
    val size: String? = null,
    val width: Long? = null,
    val height: Long? = null,
    val aspect: Double? = null,
    val original: Original? = null,
    @SerialName("small")
    val small_: Small? = null,
)
