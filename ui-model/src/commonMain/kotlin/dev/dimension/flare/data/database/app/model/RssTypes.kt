package dev.dimension.flare.data.database.app.model

import kotlinx.serialization.Serializable

@Serializable
public enum class SubscriptionType {
    RSS,
    MASTODON_TRENDS,
    MASTODON_PUBLIC,
    MASTODON_LOCAL,
}

@Serializable
public enum class RssDisplayMode {
    FULL_CONTENT,
    OPEN_IN_BROWSER,
    DESCRIPTION_ONLY,
}
