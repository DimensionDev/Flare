package dev.dimension.flare.data.network.misskey.api.model

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Poll(
    @SerialName("choices")
    val choices: List<Choice>,

    @SerialName("expiresAt")
    val expiresAt: Instant? = null,

    @SerialName("multiple")
    val multiple: Boolean,
){

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