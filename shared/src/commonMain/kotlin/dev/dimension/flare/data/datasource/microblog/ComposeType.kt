package dev.dimension.flare.data.datasource.microblog

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public enum class ComposeType {
    @SerialName("new")
    New,

    @SerialName("quote")
    Quote,

    @SerialName("reply")
    Reply,
}
