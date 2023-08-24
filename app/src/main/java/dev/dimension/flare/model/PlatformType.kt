package dev.dimension.flare.model

import kotlinx.serialization.Serializable

@Serializable
enum class PlatformType {
    Mastodon,
    Misskey,
    Bluesky
}
