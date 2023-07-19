package dev.dimension.flare.common

import dev.dimension.flare.model.MicroBlogKey

const val AppSchema = "flare"

class AppDeepLink {
    object Callback {
        const val Mastodon = "$AppSchema://Callback/SignIn/Mastodon"
        const val Twitter = "$AppSchema://Callback/SignIn/Twitter"
    }

    object Mastodon {
        object Hashtag {
            const val route = "$AppSchema://Mastodon/Hashtag/{keyword}"
            operator fun invoke(keyword: String) = "$AppSchema://Mastodon/Hashtag/${java.net.URLEncoder.encode(keyword, "UTF-8")}"
        }
    }
    object User {
        const val route = "$AppSchema://User/{userKey}"
        operator fun invoke(userKey: MicroBlogKey) = "$AppSchema://User/$userKey"
    }
}