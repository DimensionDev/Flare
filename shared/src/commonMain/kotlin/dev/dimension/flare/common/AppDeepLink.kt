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

    object StatusMedia {
        const val ROUTE = "$APPSCHEMA://StatusMedia/{accountKey}/{statusKey}/{mediaIndex}"

        operator fun invoke(
            accountKey: MicroBlogKey,
            statusKey: MicroBlogKey,
            mediaIndex: Int,
        ) = "$APPSCHEMA://StatusMedia/${accountKey.toString().encodeURLPathPart()}/${statusKey.toString().encodeURLPathPart()}/$mediaIndex"
    }
}
