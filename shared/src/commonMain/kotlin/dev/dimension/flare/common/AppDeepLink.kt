package dev.dimension.flare.common

import dev.dimension.flare.model.MicroBlogKey
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
        ) = "$APPSCHEMA://Search/$accountKey/${keyword.encodeURLPathPart()}"
    }

    object Profile {
        const val ROUTE = "$APPSCHEMA://Profile/{accountKey}/{userKey}"

        operator fun invoke(
            accountKey: MicroBlogKey,
            userKey: MicroBlogKey,
        ) = "$APPSCHEMA://Profile/$accountKey/$userKey"
    }

    object ProfileWithNameAndHost {
        const val ROUTE = "$APPSCHEMA://ProfileWithNameAndHost/{accountKey}/{userName}/{host}"

        operator fun invoke(
            accountKey: MicroBlogKey,
            userName: String,
            host: String,
        ) = "$APPSCHEMA://ProfileWithNameAndHost/$accountKey/$userName/$host"
    }

    object StatusDetail {
        const val ROUTE = "$APPSCHEMA://StatusDetail/{accountKey}/{statusKey}"

        operator fun invoke(
            accountKey: MicroBlogKey,
            statusKey: MicroBlogKey,
        ) = "$APPSCHEMA://StatusDetail/$accountKey/$statusKey"
    }

    object VVO {
        object StatusDetail {
            const val ROUTE = "$APPSCHEMA://VVO/StatusDetail/{accountKey}/{statusKey}"

            operator fun invoke(
                accountKey: MicroBlogKey,
                statusKey: MicroBlogKey,
            ) = "$APPSCHEMA://VVO/StatusDetail/$accountKey/$statusKey"
        }

        object CommentDetail {
            const val ROUTE = "$APPSCHEMA://VVO/CommentDetail/{accountKey}/{statusKey}"

            operator fun invoke(
                accountKey: MicroBlogKey,
                statusKey: MicroBlogKey,
            ) = "$APPSCHEMA://VVO/CommentDetail/$accountKey/$statusKey"
        }

        object ReplyToComment {
            const val ROUTE = "$APPSCHEMA://VVO/ReplyToComment/{accountKey}/{replyTo}/{rootId}"

            operator fun invoke(
                accountKey: MicroBlogKey,
                replyTo: MicroBlogKey,
                rootId: String,
            ) = "$APPSCHEMA://VVO/ReplyToComment/$accountKey/$replyTo/$rootId"
        }
    }

    object Compose {
        object Reply {
            const val ROUTE = "$APPSCHEMA://Compose/Reply/{accountKey}/{statusKey}"

            operator fun invoke(
                accountKey: MicroBlogKey,
                statusKey: MicroBlogKey,
            ) = "$APPSCHEMA://Compose/Reply/$accountKey/$statusKey"
        }

        object Quote {
            const val ROUTE = "$APPSCHEMA://Compose/Quote/{accountKey}/{statusKey}"

            operator fun invoke(
                accountKey: MicroBlogKey,
                statusKey: MicroBlogKey,
            ) = "$APPSCHEMA://Compose/Quote/$accountKey/$statusKey"
        }
    }

    object DeleteStatus {
        const val ROUTE = "$APPSCHEMA://DeleteStatus/{accountKey}/{statusKey}"

        operator fun invoke(
            accountKey: MicroBlogKey,
            statusKey: MicroBlogKey,
        ) = "$APPSCHEMA://DeleteStatus/$accountKey/$statusKey"
    }

    object Bluesky {
        object ReportStatus {
            const val ROUTE = "$APPSCHEMA://Bluesky/ReportStatus/{accountKey}/{statusKey}"

            operator fun invoke(
                accountKey: MicroBlogKey,
                statusKey: MicroBlogKey,
            ) = "$APPSCHEMA://Bluesky/ReportStatus/$accountKey/$statusKey"
        }
    }

    object Mastodon {
        object ReportStatus {
            const val ROUTE = "$APPSCHEMA://Mastodon/ReportStatus/{accountKey}/{statusKey}/{userKey}"

            operator fun invoke(
                accountKey: MicroBlogKey,
                statusKey: MicroBlogKey,
                userKey: MicroBlogKey,
            ) = "$APPSCHEMA://Mastodon/ReportStatus/$accountKey/$statusKey/$userKey"
        }
    }

    object Misskey {
        object ReportStatus {
            const val ROUTE = "$APPSCHEMA://Misskey/ReportStatus/{accountKey}/{statusKey}/{userKey}"

            operator fun invoke(
                accountKey: MicroBlogKey,
                statusKey: MicroBlogKey,
                userKey: MicroBlogKey,
            ) = "$APPSCHEMA://Misskey/ReportStatus/$accountKey/$statusKey/$userKey"
        }

        object AddReaction {
            const val ROUTE = "$APPSCHEMA://Misskey/AddReaction/{accountKey}/{statusKey}"

            operator fun invoke(
                accountKey: MicroBlogKey,
                statusKey: MicroBlogKey,
            ) = "$APPSCHEMA://Misskey/AddReaction/$accountKey/$statusKey"
        }
    }

    object RawImage {
        const val ROUTE = "$APPSCHEMA://RawImage/{uri}"

        operator fun invoke(url: String) = "$APPSCHEMA://RawImage/${url.encodeURLPathPart()}"
    }

    fun parse(url: String): DeeplinkEvent? {
        val uri = url.removePrefix("$APPSCHEMA://")
        return when {
            uri.startsWith("Search/") -> {
                val path = uri.substringAfter("Search/")
                val accountKey = MicroBlogKey.valueOf(path.substringBefore("/"))
                val keyword = path.substringAfter("/")
                DeeplinkEvent.Search(accountKey, keyword)
            }

            uri.startsWith("Profile/") -> {
                val path = uri.substringAfter("Profile/")
                val accountKey = MicroBlogKey.valueOf(path.substringBefore("/"))
                val userKey = MicroBlogKey.valueOf(path.substringAfter("/"))
                DeeplinkEvent.Profile(accountKey, userKey)
            }

            uri.startsWith("ProfileWithNameAndHost/") -> {
                val path = uri.substringAfter("ProfileWithNameAndHost/")
                val accountKey = MicroBlogKey.valueOf(path.substringBefore("/"))
                val userName = path.substringAfter("/").substringBefore("/")
                val host = path.substringAfter("/").substringAfter("/")
                DeeplinkEvent.ProfileWithNameAndHost(accountKey, userName, host)
            }

            uri.startsWith("StatusDetail/") -> {
                val path = uri.substringAfter("StatusDetail/")
                val accountKey = MicroBlogKey.valueOf(path.substringBefore("/"))
                val statusKey = MicroBlogKey.valueOf(path.substringAfter("/"))
                DeeplinkEvent.StatusDetail(accountKey, statusKey)
            }

            uri.startsWith("Compose") -> {
                DeeplinkEvent.Compose
            }

            uri.startsWith("RawImage/") -> {
                val rawImage = uri.substringAfter("RawImage/")
                DeeplinkEvent.RawImage(rawImage)
            }

            else -> null
        }
    }
}

sealed interface DeeplinkEvent {
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

    data object Compose : DeeplinkEvent

    data class RawImage(
        val url: String,
    ) : DeeplinkEvent
}
