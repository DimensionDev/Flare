package dev.dimension.flare.common

import com.ramcosta.composedestinations.spec.Direction

const val AppSchema = "flare"

object AppDeepLink {
    object Callback {
        const val Mastodon = "$AppSchema://Callback/SignIn/Mastodon"
        const val Misskey = "$AppSchema://Callback/SignIn/Misskey"
        const val Twitter = "$AppSchema://Callback/SignIn/Twitter"
    }

    object Search {
        const val route = "$AppSchema://Search/{keyword}"
        operator fun invoke(keyword: String) = "$AppSchema://Search/${java.net.URLEncoder.encode(keyword, "UTF-8")}"
    }
}

fun Direction.deeplink(): String {
    return "$AppSchema://${this.route}"
}
