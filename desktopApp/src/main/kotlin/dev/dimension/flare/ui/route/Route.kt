package dev.dimension.flare.ui.route

import dev.dimension.flare.data.model.TimelineTabItem
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import io.ktor.http.Url
import kotlinx.serialization.Serializable

@Serializable
internal sealed interface Route {
    sealed interface FloatingRoute : Route

    sealed interface ScreenRoute : Route

    @Serializable
    data class Timeline(
        val tabItem: TimelineTabItem,
    ) : ScreenRoute

    @Serializable
    data class Discover(
        val accountType: AccountType,
    ) : ScreenRoute

    @Serializable
    data class Notification(
        val accountType: AccountType,
    ) : ScreenRoute

    @Serializable
    data object Settings : ScreenRoute

    @Serializable
    data object Rss : ScreenRoute

    @Serializable
    data class Profile(
        val accountType: AccountType,
        val userKey: MicroBlogKey,
    ) : ScreenRoute

    @Serializable
    data class ProfileWithNameAndHost(
        val accountType: AccountType,
        val userName: String,
        val host: String,
    ) : ScreenRoute

    @Serializable
    data class MeRoute(
        val accountType: AccountType,
    ) : ScreenRoute

    @Serializable
    data object ServiceSelect : ScreenRoute

    @Serializable
    data class AllLists(
        val accountType: AccountType,
    ) : ScreenRoute

    @Serializable
    data class BlueskyFeeds(
        val accountType: AccountType,
    ) : ScreenRoute

    @Serializable
    data class DirectMessage(
        val accountType: AccountType,
    ) : ScreenRoute

    @Serializable
    data class StatusDetail(
        val accountType: AccountType,
        val statusKey: MicroBlogKey,
    ) : ScreenRoute

    data object VVO {
        @Serializable
        data class StatusDetail(
            val accountType: AccountType,
            val statusKey: MicroBlogKey,
        ) : ScreenRoute

        @Serializable
        data class CommentDetail(
            val accountType: AccountType,
            val statusKey: MicroBlogKey,
        ) : ScreenRoute
    }

    @Serializable
    data class AddReaction(
        val accountType: AccountType,
        val statusKey: MicroBlogKey,
    ) : FloatingRoute

    @Serializable
    data class DeleteStatus(
        val accountType: AccountType,
        val statusKey: MicroBlogKey,
    ) : FloatingRoute

    @Serializable
    data class BlueskyReport(
        val accountType: AccountType,
        val statusKey: MicroBlogKey,
    ) : FloatingRoute

    @Serializable
    data class MastodonReport(
        val accountType: AccountType,
        val statusKey: MicroBlogKey,
        val userKey: MicroBlogKey,
    ) : FloatingRoute

    @Serializable
    data class MisskeyReport(
        val accountType: AccountType,
        val statusKey: MicroBlogKey,
        val userKey: MicroBlogKey,
    ) : FloatingRoute

    @Serializable
    data class AltText(
        val text: String,
    ) : FloatingRoute

    @Serializable
    data class Search(
        val accountType: AccountType,
        val keyword: String,
    ) : ScreenRoute

    companion object {
        public fun parse(url: String): Route? {
            val data = Url(url)
            return when (data.host) {
                "Callback" ->
                    when (data.segments.getOrNull(0)) {
                        "SignIn" ->
                            when (data.segments.getOrNull(1)) {
//                                "Mastodon" -> Route.Callback.Mastodon
//                                "Misskey" -> Route.Callback.Misskey
                                else -> null
                            }

                        else -> null
                    }

                "Search" -> {
                    val accountKey = data.parameters["accountKey"]?.let { MicroBlogKey.valueOf(it) }
                    val keyword = data.segments.getOrNull(0) ?: return null
                    val accountType =
                        accountKey?.let { AccountType.Specific(it) } ?: AccountType.Guest
                    Route.Search(accountType = accountType, keyword = keyword)
                }

                "Profile" -> {
                    val accountKey = data.parameters["accountKey"]?.let { MicroBlogKey.valueOf(it) }
                    val userKey = MicroBlogKey.valueOf(data.segments.getOrNull(0) ?: return null)
                    val accountType =
                        accountKey?.let { AccountType.Specific(it) } ?: AccountType.Guest
                    Route.Profile(accountType, userKey)
                }

                "ProfileWithNameAndHost" -> {
                    val accountKey = data.parameters["accountKey"]?.let { MicroBlogKey.valueOf(it) }
                    val userName = data.segments.getOrNull(0) ?: return null
                    val host = data.segments.getOrNull(1) ?: return null
                    val accountType =
                        accountKey?.let { AccountType.Specific(it) } ?: AccountType.Guest
                    Route.ProfileWithNameAndHost(
                        accountType = accountType,
                        userName = userName,
                        host = host,
                    )
                }

                "StatusDetail" -> {
                    val accountKey = data.parameters["accountKey"]?.let { MicroBlogKey.valueOf(it) }
                    val statusKey = MicroBlogKey.valueOf(data.segments.getOrNull(0) ?: return null)
                    val accountType =
                        accountKey?.let { AccountType.Specific(it) } ?: AccountType.Guest
                    Route.StatusDetail(statusKey = statusKey, accountType = accountType)
                }

                "Compose" ->
                    when (data.segments.getOrNull(0)) {
                        "Reply" -> {
                            val accountKey =
                                MicroBlogKey.valueOf(data.segments.getOrNull(1) ?: return null)
                            val statusKey =
                                MicroBlogKey.valueOf(data.segments.getOrNull(2) ?: return null)
//                            Route.Compose.Reply(accountKey, statusKey)
                            null
                        }

                        "Quote" -> {
                            val accountKey =
                                MicroBlogKey.valueOf(data.segments.getOrNull(1) ?: return null)
                            val statusKey =
                                MicroBlogKey.valueOf(data.segments.getOrNull(2) ?: return null)
//                            Route.Compose.Quote(accountKey, statusKey)
                            null
                        }

                        "New" -> {
                            val accountKey =
                                MicroBlogKey.valueOf(data.segments.getOrNull(1) ?: return null)
//                            Route.Compose.New(AccountType.Specific(accountKey))
                            null
                        }

                        else -> null
                    }

                "RawImage" -> {
                    val rawImage = data.segments.getOrNull(0) ?: return null
//                    Route.Media.Image(rawImage, previewUrl = null)
                    null
                }

                "VVO" ->
                    when (data.segments.getOrNull(0)) {
                        "StatusDetail" -> {
                            val accountKey =
                                MicroBlogKey.valueOf(data.segments.getOrNull(1) ?: return null)
                            val statusKey =
                                MicroBlogKey.valueOf(data.segments.getOrNull(2) ?: return null)
                            Route.VVO.StatusDetail(
                                statusKey = statusKey,
                                accountType = AccountType.Specific(accountKey),
                            )
                        }

                        "CommentDetail" -> {
                            val accountKey =
                                MicroBlogKey.valueOf(data.segments.getOrNull(1) ?: return null)
                            val statusKey =
                                MicroBlogKey.valueOf(data.segments.getOrNull(2) ?: return null)
                            Route.VVO.CommentDetail(
                                statusKey = statusKey,
                                accountType = AccountType.Specific(accountKey),
                            )
                        }

                        "ReplyToComment" -> {
                            val accountKey =
                                MicroBlogKey.valueOf(data.segments.getOrNull(1) ?: return null)
                            val replyTo =
                                MicroBlogKey.valueOf(data.segments.getOrNull(2) ?: return null)
                            val rootId = data.segments.getOrNull(3) ?: return null
//                            Route.Compose.VVOReplyComment(accountKey, replyTo, rootId)
                            null
                        }

                        else -> null
                    }

                "DeleteStatus" -> {
                    val accountKey = MicroBlogKey.valueOf(data.segments.getOrNull(0) ?: return null)
                    val statusKey = MicroBlogKey.valueOf(data.segments.getOrNull(1) ?: return null)
                    Route.DeleteStatus(
                        statusKey = statusKey,
                        accountType = AccountType.Specific(accountKey),
                    )
                }

                "AddReaction" -> {
                    val accountKey = MicroBlogKey.valueOf(data.segments.getOrNull(0) ?: return null)
                    val statusKey = MicroBlogKey.valueOf(data.segments.getOrNull(1) ?: return null)
                    Route.AddReaction(
                        statusKey = statusKey,
                        accountType = AccountType.Specific(accountKey),
                    )
                }

                "Bluesky" ->
                    when (data.segments.getOrNull(0)) {
                        "ReportStatus" -> {
                            val accountKey =
                                MicroBlogKey.valueOf(data.segments.getOrNull(1) ?: return null)
                            val statusKey =
                                MicroBlogKey.valueOf(data.segments.getOrNull(2) ?: return null)
                            Route.BlueskyReport(
                                statusKey = statusKey,
                                accountType = AccountType.Specific(accountKey),
                            )
                        }

                        else -> null
                    }

                "Mastodon" ->
                    when (data.segments.getOrNull(0)) {
                        "ReportStatus" -> {
                            val accountKey =
                                MicroBlogKey.valueOf(data.segments.getOrNull(1) ?: return null)
                            val statusKey =
                                MicroBlogKey.valueOf(data.segments.getOrNull(2) ?: return null)
                            val userKey =
                                MicroBlogKey.valueOf(data.segments.getOrNull(3) ?: return null)
                            Route.MastodonReport(
                                statusKey = statusKey,
                                userKey = userKey,
                                accountType = AccountType.Specific(accountKey),
                            )
                        }

                        else -> null
                    }

                "Misskey" ->
                    when (data.segments.getOrNull(0)) {
                        "ReportStatus" -> {
                            val accountKey =
                                MicroBlogKey.valueOf(data.segments.getOrNull(1) ?: return null)
                            val statusKey =
                                MicroBlogKey.valueOf(data.segments.getOrNull(2) ?: return null)
                            val userKey =
                                MicroBlogKey.valueOf(data.segments.getOrNull(3) ?: return null)
                            Route.MisskeyReport(
                                accountType = AccountType.Specific(accountKey),
                                statusKey = statusKey,
                                userKey = userKey,
                            )
                        }

                        else -> null
                    }

                "StatusMedia" -> {
                    val accountKey = data.parameters["accountKey"]?.let { MicroBlogKey.valueOf(it) }
                    val statusKey = MicroBlogKey.valueOf(data.segments.getOrNull(0) ?: return null)
                    val index = data.segments.getOrNull(1)?.toIntOrNull() ?: return null
                    val accountType =
                        accountKey?.let { AccountType.Specific(it) } ?: AccountType.Guest
                    val preview = data.parameters["preview"]
//                    Route.Media.StatusMedia(accountType = accountType, statusKey = statusKey, index = index, preview = preview)
                    null
                }

                "Podcast" -> {
                    val accountKey = MicroBlogKey.valueOf(data.segments.getOrNull(0) ?: return null)
                    val id = data.segments.getOrNull(1) ?: return null
                    val accountType = accountKey.let { AccountType.Specific(it) }
//                    Route.Media.Podcast(accountType = accountType, id = id)
                    null
                }

                "AltText" -> {
                    val text = data.segments.getOrNull(0) ?: return null
//                    Route.AltText(text)
                    null
                }

                "RSS" -> {
                    val feedUrl = data.segments.getOrNull(0) ?: return null
//                    Route.Rss.Detail(feedUrl)
                    null
                }

                else -> null
            }
        }
    }
}
