package dev.dimension.flare.common

import dev.dimension.flare.model.MicroBlogKey
import io.ktor.http.encodeURLQueryComponent

const val APPSCHEMA = "flare"

object AppDeepLink {
    object Callback {
        const val MASTODON = "$APPSCHEMA://Callback/SignIn/Mastodon"
        const val MISSKEY = "$APPSCHEMA://Callback/SignIn/Misskey"
    }

    object Search {
        const val ROUTE = "$APPSCHEMA://Search/{keyword}"

        operator fun invoke(keyword: String) = "$APPSCHEMA://Search/${keyword.encodeURLQueryComponent()}"
    }

    object Profile {
        const val ROUTE = "$APPSCHEMA://Profile/{userKey}"

        operator fun invoke(userKey: MicroBlogKey) = "$APPSCHEMA://Profile/$userKey"
    }

    object ProfileWithNameAndHost {
        const val ROUTE = "$APPSCHEMA://ProfileWithNameAndHost/{userName}/{host}"

        operator fun invoke(
            userName: String,
            host: String,
        ) = "$APPSCHEMA://ProfileWithNameAndHost/${userName.encodeURLQueryComponent()}/$host"
    }

    fun parse(url: String): DeeplinkEvent? {
        val uri = url.removePrefix("$APPSCHEMA://")
        return when {
            uri.startsWith("Search/") -> {
                val keyword = uri.substringAfter("Search/")
                DeeplinkEvent.Search(keyword)
            }
            uri.startsWith("Profile/") -> {
                val userKey = uri.substringAfter("Profile/")
                DeeplinkEvent.Profile(MicroBlogKey.valueOf(userKey))
            }
            uri.startsWith("ProfileWithNameAndHost/") -> {
                val userNameAndHost = uri.substringAfter("ProfileWithNameAndHost/")
                val userName = userNameAndHost.substringBefore("/")
                val host = userNameAndHost.substringAfter("/")
                DeeplinkEvent.ProfileWithNameAndHost(userName, host)
            }
            else -> null
        }
    }
}

sealed interface DeeplinkEvent {
    data class Search(
        val keyword: String,
    ) : DeeplinkEvent

    data class Profile(
        val userKey: MicroBlogKey,
    ) : DeeplinkEvent

    data class ProfileWithNameAndHost(
        val userName: String,
        val host: String,
    ) : DeeplinkEvent
}
