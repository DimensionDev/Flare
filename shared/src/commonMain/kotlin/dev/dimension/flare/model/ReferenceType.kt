package dev.dimension.flare.model

import kotlinx.serialization.Serializable

@Serializable
enum class ReferenceType {
    Retweet,
    Reply,
    Quote,
    Notification,
}
