package dev.dimension.flare.common

import dev.dimension.flare.model.MicroBlogKey
import io.ktor.http.encodeURLPathPart
import io.ktor.http.encodeURLQueryComponent

const val APPSCHEMA = "flare"

object AppDeepLink {
    object Callback {
        const val MASTODON = "$APPSCHEMA://Callback/SignIn/Mastodon"
        const val MISSKEY = "$APPSCHEMA://Callback/SignIn/Misskey"
    }

    object Search {
        const val ROUTE = "$APPSCHEMA://Search/{keyword}?accountKey={accountKey}"

        operator fun invoke(
            accountKey: MicroBlogKey?,
            keyword: String,
        ) = "$APPSCHEMA://Search/${keyword.encodeURLPathPart()}${accountKey?.let {
            "?accountKey=${it.toString().encodeURLQueryComponent()}"
        } ?: ""}"
    }

    object Profile {
        const val ROUTE = "$APPSCHEMA://Profile/{userKey}?accountKey={accountKey}"

        operator fun invoke(
            accountKey: MicroBlogKey?,
            userKey: MicroBlogKey,
        ) = "$APPSCHEMA://Profile/${userKey.toString().encodeURLPathPart()}${accountKey?.let {
            "?accountKey=${it.toString().encodeURLQueryComponent()}"
        } ?: ""}"
    }

    object ProfileWithNameAndHost {
        const val ROUTE = "$APPSCHEMA://ProfileWithNameAndHost/{userName}/{host}?accountKey={accountKey}"

        operator fun invoke(
            accountKey: MicroBlogKey?,
            userName: String,
            host: String,
        ) = "$APPSCHEMA://ProfileWithNameAndHost/${userName.encodeURLPathPart()}/${host.encodeURLPathPart()}${accountKey?.let {
            "?accountKey=${it.toString().encodeURLQueryComponent()}"
        } ?: ""}"
    }

    object StatusDetail {
        const val ROUTE = "$APPSCHEMA://StatusDetail/{statusKey}?accountKey={accountKey}"

        operator fun invoke(
            accountKey: MicroBlogKey?,
            statusKey: MicroBlogKey,
        ) = "$APPSCHEMA://StatusDetail/${statusKey.toString().encodeURLPathPart()}${accountKey?.let {
            "?accountKey=${it.toString().encodeURLQueryComponent()}"
        } ?: ""}"
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

        object New {
            const val ROUTE = "$APPSCHEMA://Compose/New/{accountKey}"

            operator fun invoke(accountKey: MicroBlogKey) = "$APPSCHEMA://Compose/New/${accountKey.toString().encodeURLPathPart()}"
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
        const val ROUTE = "$APPSCHEMA://StatusMedia/{statusKey}/{mediaIndex}?accountKey={accountKey}&preview={preview}"

        operator fun invoke(
            accountKey: MicroBlogKey?,
            statusKey: MicroBlogKey,
            mediaIndex: Int,
            preview: String?,
        ): String {
            val query =
                listOfNotNull(
                    accountKey?.let { "accountKey=${it.toString().encodeURLQueryComponent()}" },
                    preview?.let { "preview=${it.encodeURLQueryComponent()}" },
                ).joinToString(separator = "&")
            return "$APPSCHEMA://StatusMedia/${statusKey.toString().encodeURLPathPart()}/$mediaIndex?$query"
        }
    }
}
