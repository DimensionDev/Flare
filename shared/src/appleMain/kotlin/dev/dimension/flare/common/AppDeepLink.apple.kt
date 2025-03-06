package dev.dimension.flare.common

import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import io.ktor.http.Url

public object AppDeepLinkHelper {
    public fun parse(url: String): AppleRoute? {
        val data = Url(url)
        return when (data.host) {
            "Callback" ->
                when (data.segments.getOrNull(0)) {
                    "SignIn" ->
                        when (data.segments.getOrNull(1)) {
                            "Mastodon" -> AppleRoute.Callback.Mastodon
                            "Misskey" -> AppleRoute.Callback.Misskey
                            else -> null
                        }
                    else -> null
                }

            "Search" -> {
                val accountKey = data.parameters["accountKey"]?.let { MicroBlogKey.valueOf(it) }
                val keyword = data.segments.getOrNull(0) ?: return null
                val accountType = accountKey?.let { AccountType.Specific(it) } ?: AccountType.Guest
                AppleRoute.Search(accountType, keyword)
            }

            "Profile" -> {
                val accountKey = data.parameters["accountKey"]?.let { MicroBlogKey.valueOf(it) }
                val userKey = MicroBlogKey.valueOf(data.segments.getOrNull(0) ?: return null)
                val accountType = accountKey?.let { AccountType.Specific(it) } ?: AccountType.Guest
                AppleRoute.Profile(accountType, userKey)
            }

            "ProfileWithNameAndHost" -> {
                val accountKey = data.parameters["accountKey"]?.let { MicroBlogKey.valueOf(it) }
                val userName = data.segments.getOrNull(0) ?: return null
                val host = data.segments.getOrNull(1) ?: return null
                val accountType = accountKey?.let { AccountType.Specific(it) } ?: AccountType.Guest
                AppleRoute.ProfileWithNameAndHost(accountType, userName, host)
            }

            "StatusDetail" -> {
                val accountKey = data.parameters["accountKey"]?.let { MicroBlogKey.valueOf(it) }
                val statusKey = MicroBlogKey.valueOf(data.segments.getOrNull(0) ?: return null)
                val accountType = accountKey?.let { AccountType.Specific(it) } ?: AccountType.Guest
                AppleRoute.StatusDetail(accountType, statusKey)
            }

            "Compose" ->
                when (data.segments.getOrNull(0)) {
                    "Reply" -> {
                        val accountKey = MicroBlogKey.valueOf(data.segments.getOrNull(1) ?: return null)
                        val statusKey = MicroBlogKey.valueOf(data.segments.getOrNull(2) ?: return null)
                        AppleRoute.Compose.Reply(AccountType.Specific(accountKey), statusKey)
                    }
                    "Quote" -> {
                        val accountKey = MicroBlogKey.valueOf(data.segments.getOrNull(1) ?: return null)
                        val statusKey = MicroBlogKey.valueOf(data.segments.getOrNull(2) ?: return null)
                        AppleRoute.Compose.Quote(AccountType.Specific(accountKey), statusKey)
                    }
                    "New" -> {
                        val accountKey = MicroBlogKey.valueOf(data.segments.getOrNull(1) ?: return null)
                        AppleRoute.Compose.New(AccountType.Specific(accountKey))
                    }
                    else -> null
                }

            "RawImage" -> {
                val rawImage = data.segments.getOrNull(0) ?: return null
                AppleRoute.RawImage(rawImage)
            }

            "VVO" ->
                when (data.segments.getOrNull(0)) {
                    "StatusDetail" -> {
                        val accountKey = MicroBlogKey.valueOf(data.segments.getOrNull(1) ?: return null)
                        val statusKey = MicroBlogKey.valueOf(data.segments.getOrNull(2) ?: return null)
                        AppleRoute.VVO.StatusDetail(AccountType.Specific(accountKey), statusKey)
                    }
                    "CommentDetail" -> {
                        val accountKey = MicroBlogKey.valueOf(data.segments.getOrNull(1) ?: return null)
                        val statusKey = MicroBlogKey.valueOf(data.segments.getOrNull(2) ?: return null)
                        AppleRoute.VVO.CommentDetail(AccountType.Specific(accountKey), statusKey)
                    }
                    "ReplyToComment" -> {
                        val accountKey = MicroBlogKey.valueOf(data.segments.getOrNull(1) ?: return null)
                        val replyTo = MicroBlogKey.valueOf(data.segments.getOrNull(2) ?: return null)
                        val rootId = data.segments.getOrNull(3) ?: return null
                        AppleRoute.VVO.ReplyToComment(AccountType.Specific(accountKey), replyTo, rootId)
                    }
                    else -> null
                }

            "DeleteStatus" -> {
                val accountKey = MicroBlogKey.valueOf(data.segments.getOrNull(0) ?: return null)
                val statusKey = MicroBlogKey.valueOf(data.segments.getOrNull(1) ?: return null)
                AppleRoute.DeleteStatus(AccountType.Specific(accountKey), statusKey)
            }

            "AddReaction" -> {
                val accountKey = MicroBlogKey.valueOf(data.segments.getOrNull(1) ?: return null)
                val statusKey = MicroBlogKey.valueOf(data.segments.getOrNull(2) ?: return null)
                AppleRoute.AddReaction(AccountType.Specific(accountKey), statusKey)
            }

            "Bluesky" ->
                when (data.segments.getOrNull(0)) {
                    "ReportStatus" -> {
                        val accountKey = MicroBlogKey.valueOf(data.segments.getOrNull(1) ?: return null)
                        val statusKey = MicroBlogKey.valueOf(data.segments.getOrNull(2) ?: return null)
                        AppleRoute.Bluesky.ReportStatus(AccountType.Specific(accountKey), statusKey)
                    }
                    else -> null
                }

            "Mastodon" ->
                when (data.segments.getOrNull(0)) {
                    "ReportStatus" -> {
                        val accountKey = MicroBlogKey.valueOf(data.segments.getOrNull(1) ?: return null)
                        val statusKey = MicroBlogKey.valueOf(data.segments.getOrNull(2) ?: return null)
                        val userKey = MicroBlogKey.valueOf(data.segments.getOrNull(3) ?: return null)
                        AppleRoute.Mastodon.ReportStatus(AccountType.Specific(accountKey), statusKey, userKey)
                    }
                    else -> null
                }

            "Misskey" ->
                when (data.segments.getOrNull(0)) {
                    "ReportStatus" -> {
                        val accountKey = MicroBlogKey.valueOf(data.segments.getOrNull(1) ?: return null)
                        val statusKey = MicroBlogKey.valueOf(data.segments.getOrNull(2) ?: return null)
                        val userKey = MicroBlogKey.valueOf(data.segments.getOrNull(3) ?: return null)
                        AppleRoute.Misskey.ReportStatus(AccountType.Specific(accountKey), statusKey, userKey)
                    }
                    else -> null
                }

            "StatusMedia" -> {
                val accountKey = data.parameters["accountKey"]?.let { MicroBlogKey.valueOf(it) }
                val statusKey = MicroBlogKey.valueOf(data.segments.getOrNull(0) ?: return null)
                val index = data.segments.getOrNull(1)?.toIntOrNull() ?: return null
                val accountType = accountKey?.let { AccountType.Specific(it) } ?: AccountType.Guest
                AppleRoute.StatusMedia(accountType, statusKey, index)
            }

            else -> null
        }
    }
}

public enum class RouteType {
    Screen,
    Dialog,
    Sheet,
    FullScreen,
}

public sealed class AppleRoute {
    public abstract val routeType: RouteType

    public sealed class Callback : AppleRoute() {
        public data object Mastodon : Callback() {
            override val routeType: RouteType
                get() = RouteType.Screen
        }

        public data object Misskey : Callback() {
            override val routeType: RouteType
                get() = RouteType.Screen
        }
    }

    public data class Search(
        val accountType: AccountType,
        val keyword: String,
    ) : AppleRoute() {
        override val routeType: RouteType
            get() = RouteType.Screen
    }

    public data class Profile(
        val accountType: AccountType,
        val userKey: MicroBlogKey,
    ) : AppleRoute() {
        override val routeType: RouteType
            get() = RouteType.Screen
    }

    public data class ProfileWithNameAndHost(
        val accountType: AccountType,
        val userName: String,
        val host: String,
    ) : AppleRoute() {
        override val routeType: RouteType
            get() = RouteType.Screen
    }

    public data class StatusDetail(
        val accountType: AccountType,
        val statusKey: MicroBlogKey,
    ) : AppleRoute() {
        override val routeType: RouteType
            get() = RouteType.Screen
    }

    public sealed class Compose : AppleRoute() {
        public data class New(
            val accountType: AccountType,
        ) : Compose() {
            override val routeType: RouteType
                get() = RouteType.Sheet
        }

        public data class Reply(
            val accountType: AccountType,
            val statusKey: MicroBlogKey,
        ) : Compose() {
            override val routeType: RouteType
                get() = RouteType.Sheet
        }

        public data class Quote(
            val accountType: AccountType,
            val statusKey: MicroBlogKey,
        ) : Compose() {
            override val routeType: RouteType
                get() = RouteType.Sheet
        }
    }

    public data class RawImage(
        val url: String,
    ) : AppleRoute() {
        override val routeType: RouteType
            get() = RouteType.FullScreen
    }

    public sealed class VVO : AppleRoute() {
        public data class StatusDetail(
            val accountType: AccountType,
            val statusKey: MicroBlogKey,
        ) : VVO() {
            override val routeType: RouteType
                get() = RouteType.Screen
        }

        public data class CommentDetail(
            val accountType: AccountType,
            val statusKey: MicroBlogKey,
        ) : VVO() {
            override val routeType: RouteType
                get() = RouteType.Screen
        }

        public data class ReplyToComment(
            val accountType: AccountType,
            val replyTo: MicroBlogKey,
            val rootId: String,
        ) : VVO() {
            override val routeType: RouteType
                get() = RouteType.Sheet
        }
    }

    public data class DeleteStatus(
        val accountType: AccountType,
        val statusKey: MicroBlogKey,
    ) : AppleRoute() {
        override val routeType: RouteType
            get() = RouteType.Dialog
    }

    public data class AddReaction(
        val accountType: AccountType,
        val statusKey: MicroBlogKey,
    ) : AppleRoute() {
        override val routeType: RouteType
            get() = RouteType.Sheet
    }

    public sealed class Bluesky : AppleRoute() {
        public data class ReportStatus(
            val accountType: AccountType,
            val statusKey: MicroBlogKey,
        ) : Bluesky() {
            override val routeType: RouteType
                get() = RouteType.Dialog
        }
    }

    public sealed class Mastodon : AppleRoute() {
        public data class ReportStatus(
            val accountType: AccountType,
            val statusKey: MicroBlogKey,
            val userKey: MicroBlogKey,
        ) : Mastodon() {
            override val routeType: RouteType
                get() = RouteType.Dialog
        }
    }

    public sealed class Misskey : AppleRoute() {
        public data class ReportStatus(
            val accountType: AccountType,
            val statusKey: MicroBlogKey,
            val userKey: MicroBlogKey,
        ) : Misskey() {
            override val routeType: RouteType
                get() = RouteType.Dialog
        }
    }

    public data class ProfileMedia(
        val accountType: AccountType,
        val userKey: MicroBlogKey,
    ) : AppleRoute() {
        override val routeType: RouteType
            get() = RouteType.Screen
    }

    public data class StatusMedia(
        val accountType: AccountType,
        val statusKey: MicroBlogKey,
        val index: Int,
    ) : AppleRoute() {
        override val routeType: RouteType
            get() = RouteType.FullScreen
    }
}
