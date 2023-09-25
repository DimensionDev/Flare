package dev.dimension.flare.common


const val AppSchema = "flare"

object AppDeepLink {
    object Callback {
        const val Mastodon = "$AppSchema://Callback/SignIn/Mastodon"
        const val Misskey = "$AppSchema://Callback/SignIn/Misskey"
    }
}