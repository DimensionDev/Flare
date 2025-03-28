package dev.dimension.flare.common

import dev.dimension.flare.model.MicroBlogKey
import io.ktor.http.encodeURLPathPart
import io.ktor.http.encodeURLQueryComponent

public const val APPSCHEMA: String = "flare"

public object AppDeepLink {
    public const val LOGIN: String = "$APPSCHEMA://Login"

    public object Callback {
        public const val MASTODON: String = "$APPSCHEMA://Callback/SignIn/Mastodon"
        public const val MISSKEY: String = "$APPSCHEMA://Callback/SignIn/Misskey"
    }

    public object Search {
        public const val ROUTE: String = "$APPSCHEMA://Search/{keyword}?accountKey={accountKey}"

        public operator fun invoke(
            accountKey: MicroBlogKey?,
            keyword: String,
        ): String =
            "$APPSCHEMA://Search/${keyword.encodeURLPathPart()}${accountKey?.let {
                "?accountKey=${it.toString().encodeURLQueryComponent()}"
            } ?: ""}"
    }

    public object Profile {
        public const val ROUTE: String = "$APPSCHEMA://Profile/{userKey}?accountKey={accountKey}"

        public operator fun invoke(
            accountKey: MicroBlogKey?,
            userKey: MicroBlogKey,
        ): String =
            "$APPSCHEMA://Profile/${userKey.toString().encodeURLPathPart()}${accountKey?.let {
                "?accountKey=${it.toString().encodeURLQueryComponent()}"
            } ?: ""}"
    }

    public object ProfileWithNameAndHost {
        public const val ROUTE: String = "$APPSCHEMA://ProfileWithNameAndHost/{userName}/{host}?accountKey={accountKey}"

        public operator fun invoke(
            accountKey: MicroBlogKey?,
            userName: String,
            host: String,
        ): String =
            "$APPSCHEMA://ProfileWithNameAndHost/${userName.encodeURLPathPart()}/${host.encodeURLPathPart()}${accountKey?.let {
                "?accountKey=${it.toString().encodeURLQueryComponent()}"
            } ?: ""}"
    }

    public object StatusDetail {
        public const val ROUTE: String = "$APPSCHEMA://StatusDetail/{statusKey}?accountKey={accountKey}"

        public operator fun invoke(
            accountKey: MicroBlogKey?,
            statusKey: MicroBlogKey,
        ): String =
            "$APPSCHEMA://StatusDetail/${statusKey.toString().encodeURLPathPart()}${accountKey?.let {
                "?accountKey=${it.toString().encodeURLQueryComponent()}"
            } ?: ""}"
    }

    public object VVO {
        public object StatusDetail {
            public const val ROUTE: String = "$APPSCHEMA://VVO/StatusDetail/{accountKey}/{statusKey}"

            public operator fun invoke(
                accountKey: MicroBlogKey,
                statusKey: MicroBlogKey,
            ): String =
                "$APPSCHEMA://VVO/StatusDetail/${accountKey.toString().encodeURLPathPart()}/${statusKey.toString().encodeURLPathPart()}"
        }

        public object CommentDetail {
            public const val ROUTE: String = "$APPSCHEMA://VVO/CommentDetail/{accountKey}/{statusKey}"

            public operator fun invoke(
                accountKey: MicroBlogKey,
                statusKey: MicroBlogKey,
            ): String =
                "$APPSCHEMA://VVO/CommentDetail/${accountKey.toString().encodeURLPathPart()}/${statusKey.toString().encodeURLPathPart()}"
        }

        public object ReplyToComment {
            public const val ROUTE: String = "$APPSCHEMA://VVO/ReplyToComment/{accountKey}/{replyTo}/{rootId}"

            public operator fun invoke(
                accountKey: MicroBlogKey,
                replyTo: MicroBlogKey,
                rootId: String,
            ): String =
                "$APPSCHEMA://VVO/ReplyToComment/${accountKey.toString().encodeURLPathPart()}/${replyTo.toString().encodeURLPathPart()}/${rootId.encodeURLPathPart()}"
        }
    }

    public object Compose {
        public object Reply {
            public const val ROUTE: String = "$APPSCHEMA://Compose/Reply/{accountKey}/{statusKey}"

            public operator fun invoke(
                accountKey: MicroBlogKey,
                statusKey: MicroBlogKey,
            ): String =
                "$APPSCHEMA://Compose/Reply/${accountKey.toString().encodeURLPathPart()}/${statusKey.toString().encodeURLPathPart()}"
        }

        public object Quote {
            public const val ROUTE: String = "$APPSCHEMA://Compose/Quote/{accountKey}/{statusKey}"

            public operator fun invoke(
                accountKey: MicroBlogKey,
                statusKey: MicroBlogKey,
            ): String =
                "$APPSCHEMA://Compose/Quote/${accountKey.toString().encodeURLPathPart()}/${statusKey.toString().encodeURLPathPart()}"
        }

        public object New {
            public const val ROUTE: String = "$APPSCHEMA://Compose/New/{accountKey}"

            public operator fun invoke(accountKey: MicroBlogKey): String =
                "$APPSCHEMA://Compose/New/${accountKey.toString().encodeURLPathPart()}"
        }
    }

    public object DeleteStatus {
        public const val ROUTE: String = "$APPSCHEMA://DeleteStatus/{accountKey}/{statusKey}"

        public operator fun invoke(
            accountKey: MicroBlogKey,
            statusKey: MicroBlogKey,
        ): String = "$APPSCHEMA://DeleteStatus/${accountKey.toString().encodeURLPathPart()}/${statusKey.toString().encodeURLPathPart()}"
    }

    public object AddReaction {
        public const val ROUTE: String = "$APPSCHEMA://AddReaction/{accountKey}/{statusKey}"

        public operator fun invoke(
            accountKey: MicroBlogKey,
            statusKey: MicroBlogKey,
        ): String = "$APPSCHEMA://AddReaction/${accountKey.toString().encodeURLPathPart()}/${statusKey.toString().encodeURLPathPart()}"
    }

    public object Bluesky {
        public object ReportStatus {
            public const val ROUTE: String = "$APPSCHEMA://Bluesky/ReportStatus/{accountKey}/{statusKey}"

            public operator fun invoke(
                accountKey: MicroBlogKey,
                statusKey: MicroBlogKey,
            ): String =
                "$APPSCHEMA://Bluesky/ReportStatus/${accountKey.toString().encodeURLPathPart()}/${statusKey.toString().encodeURLPathPart()}"
        }
    }

    public object Mastodon {
        public object ReportStatus {
            public const val ROUTE: String = "$APPSCHEMA://Mastodon/ReportStatus/{accountKey}/{statusKey}/{userKey}"

            public operator fun invoke(
                accountKey: MicroBlogKey,
                statusKey: MicroBlogKey,
                userKey: MicroBlogKey,
            ): String =
                "$APPSCHEMA://Mastodon/ReportStatus/${accountKey.toString().encodeURLPathPart()}/${statusKey.toString().encodeURLPathPart()}/${userKey.toString().encodeURLPathPart()}"
        }
    }

    public object Misskey {
        public object ReportStatus {
            public const val ROUTE: String = "$APPSCHEMA://Misskey/ReportStatus/{accountKey}/{statusKey}/{userKey}"

            public operator fun invoke(
                accountKey: MicroBlogKey,
                statusKey: MicroBlogKey,
                userKey: MicroBlogKey,
            ): String =
                "$APPSCHEMA://Misskey/ReportStatus/${accountKey.toString().encodeURLPathPart()}/${statusKey.toString().encodeURLPathPart()}/${userKey.toString().encodeURLPathPart()}"
        }
    }

    public object RawImage {
        public const val ROUTE: String = "$APPSCHEMA://RawImage/{uri}"

        public operator fun invoke(url: String): String = "$APPSCHEMA://RawImage/${url.encodeURLPathPart()}"
    }

    public object StatusMedia {
        public const val ROUTE: String = "$APPSCHEMA://StatusMedia/{statusKey}/{mediaIndex}?accountKey={accountKey}&preview={preview}"

        public operator fun invoke(
            accountKey: MicroBlogKey?,
            statusKey: MicroBlogKey,
            mediaIndex: Int,
            preview: String?,
        ): String {
            val query: String =
                listOfNotNull(
                    accountKey?.let { "accountKey=${it.toString().encodeURLQueryComponent()}" },
                    preview?.let { "preview=${it.encodeURLQueryComponent()}" },
                ).joinToString("&")
            return "$APPSCHEMA://StatusMedia/${statusKey.toString().encodeURLPathPart()}/$mediaIndex?$query"
        }
    }

    public object AltText {
        public const val ROUTE: String = "$APPSCHEMA://AltText/{text}"

        public operator fun invoke(text: String): String = "$APPSCHEMA://AltText/${text.encodeURLPathPart()}"
    }
}
