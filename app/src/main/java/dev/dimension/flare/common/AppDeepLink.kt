package dev.dimension.flare.common

const val AppSchema = "flare"

class AppDeepLink {
    object Callback {
        const val Mastodon = "$AppSchema://Callback/SignIn/Mastodon"
        const val Twitter = "$AppSchema://Callback/SignIn/Twitter"
    }
}