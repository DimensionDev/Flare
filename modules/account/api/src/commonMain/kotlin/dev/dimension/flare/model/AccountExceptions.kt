package dev.dimension.flare.model

public data object NoActiveAccountException : Exception("No active account.")

public data class LoginExpiredException(
    val accountKey: MicroBlogKey,
    val platformType: PlatformType,
) : Exception("Login expired.")

public data class RequireReLoginException(
    val accountKey: MicroBlogKey,
    val platformType: PlatformType,
) : Exception("Login required.")
