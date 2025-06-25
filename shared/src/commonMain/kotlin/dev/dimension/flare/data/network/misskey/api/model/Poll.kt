package dev.dimension.flare.data.network.misskey.api.model

import dev.dimension.flare.common.InstantSerializer
import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class Poll(
    @SerialName("choices")
    val choices: List<Choice>,
    @SerialName("expiresAt")
    @Serializable(with = InstantSerializer::class)
    val expiresAt: Instant? = null,
    @SerialName("multiple")
    val multiple: Boolean,
) {
    @Serializable
    data class Choice(
        @SerialName("text")
        val text: String,
        @SerialName("votes")
        val votes: Int,
        @SerialName("isVoted")
        val isVoted: Boolean = false,
    )
}
