package dev.dimension.flare.ui.route

import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey

public fun DeeplinkRoute.toWebPath(): String? =
    when (this) {
        is DeeplinkRoute.Status.Detail -> {
            "/${accountType.toWebAccountSegment()}/status/${statusKey.toWebPathSegment()}"
        }

        is DeeplinkRoute.Profile.User -> {
            "/${accountType.toWebAccountSegment()}/profile/${userKey.toWebPathSegment()}"
        }

        is DeeplinkRoute.Profile.UserNameWithHost -> {
            "/${accountType.toWebAccountSegment()}/profile/by-handle/${host.toWebPathSegment()}/${userName.toWebPathSegment()}"
        }

        is DeeplinkRoute.Search -> {
            "/search?account=${accountType.toWebAccountSegment()}&q=${query.toWebPathSegment()}"
        }

        else -> {
            null
        }
    }

private fun AccountType.toWebAccountSegment(): String =
    when (this) {
        is AccountType.Specific -> accountKey.toWebPathSegment()
        AccountType.Guest -> "guest"
        is AccountType.GuestHost -> "guest@$host".toWebPathSegment()
    }

private fun MicroBlogKey.toWebPathSegment(): String = toString().toWebPathSegment()

private fun String.toWebPathSegment(): String =
    buildString {
        for (byte in encodeToByteArray()) {
            val value = byte.toInt() and 0xff
            val char = value.toChar()
            if (char.isWebPathSegmentSafe()) {
                append(char)
            } else {
                append('%')
                append(value.toString(16).uppercase().padStart(2, '0'))
            }
        }
    }

private fun Char.isWebPathSegmentSafe(): Boolean =
    this in 'a'..'z' ||
        this in 'A'..'Z' ||
        this in '0'..'9' ||
        this == '-' ||
        this == '_' ||
        this == '.' ||
        this == '~' ||
        this == '@'
