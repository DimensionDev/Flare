package dev.dimension.flare.model

import kotlinx.serialization.Serializable

@Serializable
internal enum class ReferenceType {
    Retweet,
    Reply,
    Quote,
    Notification,
}
