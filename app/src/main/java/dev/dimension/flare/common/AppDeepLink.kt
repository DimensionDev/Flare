package dev.dimension.flare.common

import com.ramcosta.composedestinations.spec.Direction

const val AppSchema = "flare"

object AppDeepLink {
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
}

fun Direction.deeplink(): String {
    return "$AppSchema://${this.route}"
}