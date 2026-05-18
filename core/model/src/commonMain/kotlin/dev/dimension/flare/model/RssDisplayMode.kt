package dev.dimension.flare.model

import kotlinx.serialization.Serializable

@Serializable
public enum class RssDisplayMode {
    FULL_CONTENT,
    OPEN_IN_BROWSER,
    DESCRIPTION_ONLY,
}
