package dev.dimension.flare.common

import dev.dimension.flare.model.MicroBlogKey
import io.ktor.http.Url
import io.ktor.http.encodeURLPathPart

const val APPSCHEMA = "flare"

object AppDeepLink {
    object Callback {
        const val MASTODON = "$APPSCHEMA://Callback/SignIn/Mastodon"
        const val MISSKEY = "$APPSCHEMA://Callback/SignIn/Misskey"
    }

    object Search {
        const val ROUTE = "$APPSCHEMA://Search/{accountKey}/{keyword}"

        operator fun invoke(
            accountKey: MicroBlogKey,
            keyword: String,
        ) = "$APPSCHEMA://Search/${accountKey.toString().encodeURLPathPart()}/${keyword.encodeURLPathPart()}"
    }

    object Profile {
        const val ROUTE = "$APPSCHEMA://Profile/{accountKey}/{userKey}"

        operator fun invoke(
            accountKey: MicroBlogKey,
            userKey: MicroBlogKey,
        ) = "$APPSCHEMA://Profile/${accountKey.toString().encodeURLPathPart()}/${userKey.toString().encodeURLPathPart()}"
    }

    object ProfileWithNameAndHost {
        const val ROUTE = "$APPSCHEMA://ProfileWithNameAndHost/{accountKey}/{userName}/{host}"

        operator fun invoke(
            accountKey: MicroBlogKey,
            userName: String,
            host: String,
        ) =
            "$APPSCHEMA://ProfileWithNameAndHost/${accountKey.toString().encodeURLPathPart()}/${userName.encodeURLPathPart()}/${host.encodeURLPathPart()}"
    }

    object StatusDetail {
        const val ROUTE = "$APPSCHEMA://StatusDetail/{accountKey}/{statusKey}"

        operator fun invoke(
            accountKey: MicroBlogKey,
            statusKey: MicroBlogKey,
        ) = "$APPSCHEMA://StatusDetail/${accountKey.toString().encodeURLPathPart()}/${statusKey.toString().encodeURLPathPart()}"
    }

    object VVO {
        object StatusDetail {
            const val ROUTE = "$APPSCHEMA://VVO/StatusDetail/{accountKey}/{statusKey}"

            operator fun invoke(
                accountKey: MicroBlogKey,
                statusKey: MicroBlogKey,
            ) = "$APPSCHEMA://VVO/StatusDetail/${accountKey.toString().encodeURLPathPart()}/${statusKey.toString().encodeURLPathPart()}"
        }

        object CommentDetail {
            const val ROUTE = "$APPSCHEMA://VVO/CommentDetail/{accountKey}/{statusKey}"

            operator fun invoke(
                accountKey: MicroBlogKey,
                statusKey: MicroBlogKey,
            ) = "$APPSCHEMA://VVO/CommentDetail/${accountKey.toString().encodeURLPathPart()}/${statusKey.toString().encodeURLPathPart()}"
        }

        object ReplyToComment {
            const val ROUTE = "$APPSCHEMA://VVO/ReplyToComment/{accountKey}/{replyTo}/{rootId}"

            operator fun invoke(
                accountKey: MicroBlogKey,
                replyTo: MicroBlogKey,
                rootId: String,
            ) =
                "$APPSCHEMA://VVO/ReplyToComment/${accountKey.toString().encodeURLPathPart()}/${replyTo.toString().encodeURLPathPart()}/${rootId.encodeURLPathPart()}"
        }
    }

    object Compose {
        object Reply {
            const val ROUTE = "$APPSCHEMA://Compose/Reply/{accountKey}/{statusKey}"

            operator fun invoke(
                accountKey: MicroBlogKey,
                statusKey: MicroBlogKey,
            ) = "$APPSCHEMA://Compose/Reply/${accountKey.toString().encodeURLPathPart()}/${statusKey.toString().encodeURLPathPart()}"
        }

        object Quote {
            const val ROUTE = "$APPSCHEMA://Compose/Quote/{accountKey}/{statusKey}"

            operator fun invoke(
                accountKey: MicroBlogKey,
                statusKey: MicroBlogKey,
            ) = "$APPSCHEMA://Compose/Quote/${accountKey.toString().encodeURLPathPart()}/${statusKey.toString().encodeURLPathPart()}"
        }
    }

    object DeleteStatus {
        const val ROUTE = "$APPSCHEMA://DeleteStatus/{accountKey}/{statusKey}"

        operator fun invoke(
            accountKey: MicroBlogKey,
            statusKey: MicroBlogKey,
        ) = "$APPSCHEMA://DeleteStatus/${accountKey.toString().encodeURLPathPart()}/${statusKey.toString().encodeURLPathPart()}"
    }

    object Bluesky {
        object ReportStatus {
            const val ROUTE = "$APPSCHEMA://Bluesky/ReportStatus/{accountKey}/{statusKey}"

            operator fun invoke(
                accountKey: MicroBlogKey,
                statusKey: MicroBlogKey,
            ) = "$APPSCHEMA://Bluesky/ReportStatus/${accountKey.toString().encodeURLPathPart()}/${statusKey.toString().encodeURLPathPart()}"
        }
    }

    object Mastodon {
        object ReportStatus {
            const val ROUTE = "$APPSCHEMA://Mastodon/ReportStatus/{accountKey}/{statusKey}/{userKey}"

            operator fun invoke(
                accountKey: MicroBlogKey,
                statusKey: MicroBlogKey,
                userKey: MicroBlogKey,
            ) =
                "$APPSCHEMA://Mastodon/ReportStatus/${accountKey.toString().encodeURLPathPart()}/${statusKey.toString().encodeURLPathPart()}/${userKey.toString().encodeURLPathPart()}"
        }
    }

    object Misskey {
        object ReportStatus {
            const val ROUTE = "$APPSCHEMA://Misskey/ReportStatus/{accountKey}/{statusKey}/{userKey}"

            operator fun invoke(
                accountKey: MicroBlogKey,
                statusKey: MicroBlogKey,
                userKey: MicroBlogKey,
            ) =
                "$APPSCHEMA://Misskey/ReportStatus/${accountKey.toString().encodeURLPathPart()}/${statusKey.toString().encodeURLPathPart()}/${userKey.toString().encodeURLPathPart()}"
        }

        object AddReaction {
            const val ROUTE = "$APPSCHEMA://Misskey/AddReaction/{accountKey}/{statusKey}"

            operator fun invoke(
                accountKey: MicroBlogKey,
                statusKey: MicroBlogKey,
            ) = "$APPSCHEMA://Misskey/AddReaction/${accountKey.toString().encodeURLPathPart()}/${statusKey.toString().encodeURLPathPart()}"
        }
    }

    object RawImage {
        const val ROUTE = "$APPSCHEMA://RawImage/{uri}"

        operator fun invoke(url: String) = "$APPSCHEMA://RawImage/${url.encodeURLPathPart()}"
    }

    fun parse(url: String): DeeplinkEvent? {
        val data = Url(url)
        return when (data.pathSegments.getOrNull(0)) {
            "Callback" ->
                when (data.pathSegments.getOrNull(1)) {
                    "SignIn" ->
                        when (data.pathSegments.getOrNull(2)) {
                            "Mastodon" -> DeeplinkEvent.Callback.Mastodon
                            "Misskey" -> DeeplinkEvent.Callback.Misskey
                            else -> null
                        }
                    else -> null
                }

            "Search" -> {
                val accountKey = MicroBlogKey.valueOf(data.pathSegments.getOrNull(1) ?: return null)
                val keyword = data.pathSegments.getOrNull(2) ?: return null
                DeeplinkEvent.Search(accountKey, keyword)
            }

            "Profile" -> {
                val accountKey = MicroBlogKey.valueOf(data.pathSegments.getOrNull(1) ?: return null)
                val userKey = MicroBlogKey.valueOf(data.pathSegments.getOrNull(2) ?: return null)
                DeeplinkEvent.Profile(accountKey, userKey)
            }

            "ProfileWithNameAndHost" -> {
                val accountKey = MicroBlogKey.valueOf(data.pathSegments.getOrNull(1) ?: return null)
                val userName = data.pathSegments.getOrNull(2) ?: return null
                val host = data.pathSegments.getOrNull(3) ?: return null
                DeeplinkEvent.ProfileWithNameAndHost(accountKey, userName, host)
            }

            "StatusDetail" -> {
                val accountKey = MicroBlogKey.valueOf(data.pathSegments.getOrNull(1) ?: return null)
                val statusKey = MicroBlogKey.valueOf(data.pathSegments.getOrNull(2) ?: return null)
                DeeplinkEvent.StatusDetail(accountKey, statusKey)
            }

            "Compose" ->
                when (data.pathSegments.getOrNull(1)) {
                    "Reply" -> {
                        val accountKey = MicroBlogKey.valueOf(data.pathSegments.getOrNull(2) ?: return null)
                        val statusKey = MicroBlogKey.valueOf(data.pathSegments.getOrNull(3) ?: return null)
                        DeeplinkEvent.Compose.Reply(accountKey, statusKey)
                    }
                    "Quote" -> {
                        val accountKey = MicroBlogKey.valueOf(data.pathSegments.getOrNull(2) ?: return null)
                        val statusKey = MicroBlogKey.valueOf(data.pathSegments.getOrNull(3) ?: return null)
                        DeeplinkEvent.Compose.Quote(accountKey, statusKey)
                    }
                    else -> null
                }

            "RawImage" -> {
                val rawImage = data.pathSegments.getOrNull(1) ?: return null
                DeeplinkEvent.RawImage(rawImage)
            }

            "VVO" ->
                when (data.pathSegments.getOrNull(1)) {
                    "StatusDetail" -> {
                        val accountKey = MicroBlogKey.valueOf(data.pathSegments.getOrNull(2) ?: return null)
                        val statusKey = MicroBlogKey.valueOf(data.pathSegments.getOrNull(3) ?: return null)
                        DeeplinkEvent.VVO.StatusDetail(accountKey, statusKey)
                    }
                    "CommentDetail" -> {
                        val accountKey = MicroBlogKey.valueOf(data.pathSegments.getOrNull(2) ?: return null)
                        val statusKey = MicroBlogKey.valueOf(data.pathSegments.getOrNull(3) ?: return null)
                        DeeplinkEvent.VVO.CommentDetail(accountKey, statusKey)
                    }
                    "ReplyToComment" -> {
                        val accountKey = MicroBlogKey.valueOf(data.pathSegments.getOrNull(2) ?: return null)
                        val replyTo = MicroBlogKey.valueOf(data.pathSegments.getOrNull(3) ?: return null)
                        val rootId = data.pathSegments.getOrNull(4) ?: return null
                        DeeplinkEvent.VVO.ReplyToComment(accountKey, replyTo, rootId)
                    }
                    else -> null
                }

            "DeleteStatus" -> {
                val accountKey = MicroBlogKey.valueOf(data.pathSegments.getOrNull(1) ?: return null)
                val statusKey = MicroBlogKey.valueOf(data.pathSegments.getOrNull(2) ?: return null)
                DeeplinkEvent.DeleteStatus(accountKey, statusKey)
            }

            "Bluesky" ->
                when (data.pathSegments.getOrNull(1)) {
                    "ReportStatus" -> {
                        val accountKey = MicroBlogKey.valueOf(data.pathSegments.getOrNull(2) ?: return null)
                        val statusKey = MicroBlogKey.valueOf(data.pathSegments.getOrNull(3) ?: return null)
                        DeeplinkEvent.Bluesky.ReportStatus(accountKey, statusKey)
                    }
                    else -> null
                }

            "Mastodon" ->
                when (data.pathSegments.getOrNull(1)) {
                    "ReportStatus" -> {
                        val accountKey = MicroBlogKey.valueOf(data.pathSegments.getOrNull(2) ?: return null)
                        val statusKey = MicroBlogKey.valueOf(data.pathSegments.getOrNull(3) ?: return null)
                        val userKey = MicroBlogKey.valueOf(data.pathSegments.getOrNull(4) ?: return null)
                        DeeplinkEvent.Mastodon.ReportStatus(accountKey, statusKey, userKey)
                    }
                    else -> null
                }

            "Misskey" ->
                when (data.pathSegments.getOrNull(1)) {
                    "ReportStatus" -> {
                        val accountKey = MicroBlogKey.valueOf(data.pathSegments.getOrNull(2) ?: return null)
                        val statusKey = MicroBlogKey.valueOf(data.pathSegments.getOrNull(3) ?: return null)
                        val userKey = MicroBlogKey.valueOf(data.pathSegments.getOrNull(4) ?: return null)
                        DeeplinkEvent.Misskey.ReportStatus(accountKey, statusKey, userKey)
                    }
                    "AddReaction" -> {
                        val accountKey = MicroBlogKey.valueOf(data.pathSegments.getOrNull(2) ?: return null)
                        val statusKey = MicroBlogKey.valueOf(data.pathSegments.getOrNull(3) ?: return null)
                        DeeplinkEvent.Misskey.AddReaction(accountKey, statusKey)
                    }
                    else -> null
                }

            else -> null
        }
    }
}

sealed interface DeeplinkEvent {
    interface Callback : DeeplinkEvent {
        data object Mastodon : Callback

        data object Misskey : Callback
    }

    data class Search(
        val accountKey: MicroBlogKey,
        val keyword: String,
    ) : DeeplinkEvent

    data class Profile(
        val accountKey: MicroBlogKey,
        val userKey: MicroBlogKey,
    ) : DeeplinkEvent

    data class ProfileWithNameAndHost(
        val accountKey: MicroBlogKey,
        val userName: String,
        val host: String,
    ) : DeeplinkEvent

    data class StatusDetail(
        val accountKey: MicroBlogKey,
        val statusKey: MicroBlogKey,
    ) : DeeplinkEvent

    interface Compose : DeeplinkEvent {
        data class Reply(
            val accountKey: MicroBlogKey,
            val statusKey: MicroBlogKey,
        ) : Compose

        data class Quote(
            val accountKey: MicroBlogKey,
            val statusKey: MicroBlogKey,
        ) : Compose
    }

    data class RawImage(
        val url: String,
    ) : DeeplinkEvent

    interface VVO : DeeplinkEvent {
        data class StatusDetail(
            val accountKey: MicroBlogKey,
            val statusKey: MicroBlogKey,
        ) : VVO

        data class CommentDetail(
            val accountKey: MicroBlogKey,
            val statusKey: MicroBlogKey,
        ) : VVO

        data class ReplyToComment(
            val accountKey: MicroBlogKey,
            val replyTo: MicroBlogKey,
            val rootId: String,
        ) : VVO
    }

    data class DeleteStatus(
        val accountKey: MicroBlogKey,
        val statusKey: MicroBlogKey,
    ) : DeeplinkEvent

    interface Bluesky : DeeplinkEvent {
        data class ReportStatus(
            val accountKey: MicroBlogKey,
            val statusKey: MicroBlogKey,
        ) : Bluesky
    }

    interface Mastodon : DeeplinkEvent {
        data class ReportStatus(
            val accountKey: MicroBlogKey,
            val statusKey: MicroBlogKey,
            val userKey: MicroBlogKey,
        ) : Mastodon
    }

    interface Misskey : DeeplinkEvent {
        data class ReportStatus(
            val accountKey: MicroBlogKey,
            val statusKey: MicroBlogKey,
            val userKey: MicroBlogKey,
        ) : Misskey

        data class AddReaction(
            val accountKey: MicroBlogKey,
            val statusKey: MicroBlogKey,
        ) : Misskey
    }
}
