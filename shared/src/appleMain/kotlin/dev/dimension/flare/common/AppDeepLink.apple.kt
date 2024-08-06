package dev.dimension.flare.common

import dev.dimension.flare.model.MicroBlogKey
import io.ktor.http.Url

object AppDeepLinkHelper {
    fun parse(url: String): AppleRoute? {
        val data = Url(url)
        return when (data.host) {
            "Callback" ->
                when (data.pathSegments.getOrNull(1)) {
                    "SignIn" ->
                        when (data.pathSegments.getOrNull(2)) {
                            "Mastodon" -> AppleRoute.Callback.Mastodon
                            "Misskey" -> AppleRoute.Callback.Misskey
                            else -> null
                        }
                    else -> null
                }

            "Search" -> {
                val accountKey = MicroBlogKey.valueOf(data.pathSegments.getOrNull(1) ?: return null)
                val keyword = data.pathSegments.getOrNull(2) ?: return null
                AppleRoute.Search(accountKey, keyword)
            }

            "Profile" -> {
                val accountKey = MicroBlogKey.valueOf(data.pathSegments.getOrNull(1) ?: return null)
                val userKey = MicroBlogKey.valueOf(data.pathSegments.getOrNull(2) ?: return null)
                AppleRoute.Profile(accountKey, userKey)
            }

            "ProfileWithNameAndHost" -> {
                val accountKey = MicroBlogKey.valueOf(data.pathSegments.getOrNull(1) ?: return null)
                val userName = data.pathSegments.getOrNull(2) ?: return null
                val host = data.pathSegments.getOrNull(3) ?: return null
                AppleRoute.ProfileWithNameAndHost(accountKey, userName, host)
            }

            "StatusDetail" -> {
                val accountKey = MicroBlogKey.valueOf(data.pathSegments.getOrNull(1) ?: return null)
                val statusKey = MicroBlogKey.valueOf(data.pathSegments.getOrNull(2) ?: return null)
                AppleRoute.StatusDetail(accountKey, statusKey)
            }

            "Compose" ->
                when (data.pathSegments.getOrNull(1)) {
                    "Reply" -> {
                        val accountKey = MicroBlogKey.valueOf(data.pathSegments.getOrNull(2) ?: return null)
                        val statusKey = MicroBlogKey.valueOf(data.pathSegments.getOrNull(3) ?: return null)
                        AppleRoute.Compose.Reply(accountKey, statusKey)
                    }
                    "Quote" -> {
                        val accountKey = MicroBlogKey.valueOf(data.pathSegments.getOrNull(2) ?: return null)
                        val statusKey = MicroBlogKey.valueOf(data.pathSegments.getOrNull(3) ?: return null)
                        AppleRoute.Compose.Quote(accountKey, statusKey)
                    }
                    else -> null
                }

            "RawImage" -> {
                val rawImage = data.pathSegments.getOrNull(1) ?: return null
                AppleRoute.RawImage(rawImage)
            }

            "VVO" ->
                when (data.pathSegments.getOrNull(1)) {
                    "StatusDetail" -> {
                        val accountKey = MicroBlogKey.valueOf(data.pathSegments.getOrNull(2) ?: return null)
                        val statusKey = MicroBlogKey.valueOf(data.pathSegments.getOrNull(3) ?: return null)
                        AppleRoute.VVO.StatusDetail(accountKey, statusKey)
                    }
                    "CommentDetail" -> {
                        val accountKey = MicroBlogKey.valueOf(data.pathSegments.getOrNull(2) ?: return null)
                        val statusKey = MicroBlogKey.valueOf(data.pathSegments.getOrNull(3) ?: return null)
                        AppleRoute.VVO.CommentDetail(accountKey, statusKey)
                    }
                    "ReplyToComment" -> {
                        val accountKey = MicroBlogKey.valueOf(data.pathSegments.getOrNull(2) ?: return null)
                        val replyTo = MicroBlogKey.valueOf(data.pathSegments.getOrNull(3) ?: return null)
                        val rootId = data.pathSegments.getOrNull(4) ?: return null
                        AppleRoute.VVO.ReplyToComment(accountKey, replyTo, rootId)
                    }
                    else -> null
                }

            "DeleteStatus" -> {
                val accountKey = MicroBlogKey.valueOf(data.pathSegments.getOrNull(1) ?: return null)
                val statusKey = MicroBlogKey.valueOf(data.pathSegments.getOrNull(2) ?: return null)
                AppleRoute.DeleteStatus(accountKey, statusKey)
            }

            "Bluesky" ->
                when (data.pathSegments.getOrNull(1)) {
                    "ReportStatus" -> {
                        val accountKey = MicroBlogKey.valueOf(data.pathSegments.getOrNull(2) ?: return null)
                        val statusKey = MicroBlogKey.valueOf(data.pathSegments.getOrNull(3) ?: return null)
                        AppleRoute.Bluesky.ReportStatus(accountKey, statusKey)
                    }
                    else -> null
                }

            "Mastodon" ->
                when (data.pathSegments.getOrNull(1)) {
                    "ReportStatus" -> {
                        val accountKey = MicroBlogKey.valueOf(data.pathSegments.getOrNull(2) ?: return null)
                        val statusKey = MicroBlogKey.valueOf(data.pathSegments.getOrNull(3) ?: return null)
                        val userKey = MicroBlogKey.valueOf(data.pathSegments.getOrNull(4) ?: return null)
                        AppleRoute.Mastodon.ReportStatus(accountKey, statusKey, userKey)
                    }
                    else -> null
                }

            "Misskey" ->
                when (data.pathSegments.getOrNull(1)) {
                    "ReportStatus" -> {
                        val accountKey = MicroBlogKey.valueOf(data.pathSegments.getOrNull(2) ?: return null)
                        val statusKey = MicroBlogKey.valueOf(data.pathSegments.getOrNull(3) ?: return null)
                        val userKey = MicroBlogKey.valueOf(data.pathSegments.getOrNull(4) ?: return null)
                        AppleRoute.Misskey.ReportStatus(accountKey, statusKey, userKey)
                    }
                    "AddReaction" -> {
                        val accountKey = MicroBlogKey.valueOf(data.pathSegments.getOrNull(2) ?: return null)
                        val statusKey = MicroBlogKey.valueOf(data.pathSegments.getOrNull(3) ?: return null)
                        AppleRoute.Misskey.AddReaction(accountKey, statusKey)
                    }
                    else -> null
                }

            else -> null
        }
    }
}

enum class RouteType {
    Screen,
    Dialog,
    Sheet,
    FullScreen,
}

sealed class AppleRoute {
    abstract val routeType: RouteType

    sealed class Callback : AppleRoute() {
        data object Mastodon : Callback() {
            override val routeType: RouteType
                get() = RouteType.Screen
        }

        data object Misskey : Callback() {
            override val routeType: RouteType
                get() = RouteType.Screen
        }
    }

    data class Search(
        val accountKey: MicroBlogKey,
        val keyword: String,
    ) : AppleRoute() {
        override val routeType: RouteType
            get() = RouteType.Screen
    }

    data class Profile(
        val accountKey: MicroBlogKey,
        val userKey: MicroBlogKey,
    ) : AppleRoute() {
        override val routeType: RouteType
            get() = RouteType.Screen
    }

    data class ProfileWithNameAndHost(
        val accountKey: MicroBlogKey,
        val userName: String,
        val host: String,
    ) : AppleRoute() {
        override val routeType: RouteType
            get() = RouteType.Screen
    }

    data class StatusDetail(
        val accountKey: MicroBlogKey,
        val statusKey: MicroBlogKey,
    ) : AppleRoute() {
        override val routeType: RouteType
            get() = RouteType.Screen
    }

    sealed class Compose : AppleRoute() {
        data class New(
            val accountKey: MicroBlogKey,
        ) : Compose() {
            override val routeType: RouteType
                get() = RouteType.Sheet
        }

        data class Reply(
            val accountKey: MicroBlogKey,
            val statusKey: MicroBlogKey,
        ) : Compose() {
            override val routeType: RouteType
                get() = RouteType.Sheet
        }

        data class Quote(
            val accountKey: MicroBlogKey,
            val statusKey: MicroBlogKey,
        ) : Compose() {
            override val routeType: RouteType
                get() = RouteType.Sheet
        }
    }

    data class RawImage(
        val url: String,
    ) : AppleRoute() {
        override val routeType: RouteType
            get() = RouteType.FullScreen
    }

    sealed class VVO : AppleRoute() {
        data class StatusDetail(
            val accountKey: MicroBlogKey,
            val statusKey: MicroBlogKey,
        ) : VVO() {
            override val routeType: RouteType
                get() = RouteType.Screen
        }

        data class CommentDetail(
            val accountKey: MicroBlogKey,
            val statusKey: MicroBlogKey,
        ) : VVO() {
            override val routeType: RouteType
                get() = RouteType.Screen
        }

        data class ReplyToComment(
            val accountKey: MicroBlogKey,
            val replyTo: MicroBlogKey,
            val rootId: String,
        ) : VVO() {
            override val routeType: RouteType
                get() = RouteType.Sheet
        }
    }

    data class DeleteStatus(
        val accountKey: MicroBlogKey,
        val statusKey: MicroBlogKey,
    ) : AppleRoute() {
        override val routeType: RouteType
            get() = RouteType.Dialog
    }

    sealed class Bluesky : AppleRoute() {
        data class ReportStatus(
            val accountKey: MicroBlogKey,
            val statusKey: MicroBlogKey,
        ) : Bluesky() {
            override val routeType: RouteType
                get() = RouteType.Dialog
        }
    }

    sealed class Mastodon : AppleRoute() {
        data class ReportStatus(
            val accountKey: MicroBlogKey,
            val statusKey: MicroBlogKey,
            val userKey: MicroBlogKey,
        ) : Mastodon() {
            override val routeType: RouteType
                get() = RouteType.Dialog
        }
    }

    sealed class Misskey : AppleRoute() {
        data class ReportStatus(
            val accountKey: MicroBlogKey,
            val statusKey: MicroBlogKey,
            val userKey: MicroBlogKey,
        ) : Misskey() {
            override val routeType: RouteType
                get() = RouteType.Dialog
        }

        data class AddReaction(
            val accountKey: MicroBlogKey,
            val statusKey: MicroBlogKey,
        ) : Misskey() {
            override val routeType: RouteType
                get() = RouteType.Sheet
        }
    }

    data class ProfileMedia(
        val accountKey: MicroBlogKey,
        val userKey: MicroBlogKey,
    ) : AppleRoute() {
        override val routeType: RouteType
            get() = RouteType.Screen
    }

    data class StatusMedia(
        val accountKey: MicroBlogKey,
        val statusKey: MicroBlogKey,
        val index: Int,
    ) : AppleRoute() {
        override val routeType: RouteType
            get() = RouteType.FullScreen
    }
}
