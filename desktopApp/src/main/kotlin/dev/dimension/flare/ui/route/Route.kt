package dev.dimension.flare.ui.route

import dev.dimension.flare.model.MicroBlogKey
import kotlinx.serialization.Serializable

@Serializable
internal sealed interface Route {
    @Serializable
    data object Home : Route

    @Serializable
    data object Discover : Route

    @Serializable
    data object Settings : Route

    @Serializable
    data class Profile(
        val key: MicroBlogKey,
    ) : Route

    @Serializable
    data object ServiceSelect : Route
}
