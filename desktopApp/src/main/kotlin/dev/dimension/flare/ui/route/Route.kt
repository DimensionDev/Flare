package dev.dimension.flare.ui.route

import dev.dimension.flare.data.model.TimelineTabItem
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.serialization.Serializable

@Serializable
internal sealed interface Route {
    @Serializable
    data class Timeline(
        val tabItem: TimelineTabItem,
    ) : Route

    @Serializable
    data class Discover(
        val accountType: AccountType,
    ) : Route

    @Serializable
    data class Notification(
        val accountType: AccountType,
    ) : Route

    @Serializable
    data object Settings : Route

    @Serializable
    data object Rss : Route

    @Serializable
    data class Profile(
        val accountType: AccountType,
        val userKey: MicroBlogKey,
    ) : Route

    @Serializable
    data class MeRoute(
        val accountType: AccountType,
    ) : Route

    @Serializable
    data object ServiceSelect : Route

    @Serializable
    data class AllLists(
        val accountType: AccountType,
    ) : Route

    @Serializable
    data class BlueskyFeeds(
        val accountType: AccountType,
    ) : Route

    @Serializable
    data class DirectMessage(
        val accountType: AccountType,
    ) : Route
}
