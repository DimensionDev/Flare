package dev.dimension.flare.common

import dev.dimension.flare.model.MicroBlogKey
import io.ktor.http.encodeURLQueryComponent


const val AppSchema = "flare"

object AppDeepLink {
    object Callback {
        const val Mastodon = "$AppSchema://Callback/SignIn/Mastodon"
        const val Misskey = "$AppSchema://Callback/SignIn/Misskey"
    }

    object Search {
        const val route = "$AppSchema://Search/{keyword}"
        operator fun invoke(keyword: String) = "$AppSchema://Search/${keyword.encodeURLQueryComponent()}"
    }

    object Profile {
        const val route = "$AppSchema://Profile/{userKey}"
        operator fun invoke(userKey: MicroBlogKey) = "$AppSchema://Profile/$userKey"
    }

    object ProfileWithNameAndHost {
        const val route = "$AppSchema://ProfileWithNameAndHost/{userName}/{host}"
        operator fun invoke(userName: String, host: String) = "$AppSchema://ProfileWithNameAndHost/${userName.encodeURLQueryComponent()}/$host"
    }
}