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
        const val ROUTE = "$APPSCHEMA://Search/{accountKey}/{keyword}"

        operator fun invoke(
            accountKey: MicroBlogKey,
            keyword: String,
        ) = "$APPSCHEMA://Search/$accountKey/${keyword.encodeURLQueryComponent()}"
    }

    object Profile {
        const val ROUTE = "$APPSCHEMA://Profile/{accountKey}/{userKey}"

        operator fun invoke(
            accountKey: MicroBlogKey,
            userKey: MicroBlogKey,
        ) = "$APPSCHEMA://Profile/$accountKey/$userKey"
    }

    object ProfileWithNameAndHost {
        const val ROUTE = "$APPSCHEMA://ProfileWithNameAndHost/{accountKey}/{userName}/{host}"

        operator fun invoke(
            accountKey: MicroBlogKey,
            userName: String,
            host: String,
        ) = "$APPSCHEMA://ProfileWithNameAndHost/$accountKey/$userName/$host"
    }

    object StatusDetail {
        const val ROUTE = "$APPSCHEMA://StatusDetail/{statusKey}"

        operator fun invoke(statusKey: MicroBlogKey) = "$APPSCHEMA://StatusDetail/$statusKey"
    }

    object Compose {
        const val ROUTE = "$APPSCHEMA://Compose"

        operator fun invoke() = "$APPSCHEMA://Compose"
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

            uri.startsWith("StatusDetail/") -> {
                val statusKey = uri.substringAfter("StatusDetail/")
                DeeplinkEvent.StatusDetail(MicroBlogKey.valueOf(statusKey))
            }

            uri.startsWith("Compose") -> {
                DeeplinkEvent.Compose
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

    data class StatusDetail(
        val statusKey: MicroBlogKey,
    ) : DeeplinkEvent

    data object Compose : DeeplinkEvent
}
