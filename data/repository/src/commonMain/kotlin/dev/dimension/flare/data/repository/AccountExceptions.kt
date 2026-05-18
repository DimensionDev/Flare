package dev.dimension.flare.data.repository

import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType

public data object NoActiveAccountException : Exception("No active account.")

public data class LoginExpiredException(
    val accountKey: MicroBlogKey,
    val platformType: PlatformType,
) : Exception("Login expired.")

public data class RequireReLoginException(
    val accountKey: MicroBlogKey,
    val platformType: PlatformType,
) : Exception("Login required.")
