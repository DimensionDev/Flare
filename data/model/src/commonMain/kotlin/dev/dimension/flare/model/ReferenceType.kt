package dev.dimension.flare.model

import kotlinx.serialization.Serializable

@Serializable
public enum class ReferenceType {
    Retweet,
    Reply,
    Quote,
    Notification,
}
