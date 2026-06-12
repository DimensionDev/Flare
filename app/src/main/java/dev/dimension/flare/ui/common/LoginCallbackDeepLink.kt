package dev.dimension.flare.ui.common

import dev.dimension.flare.ui.route.APPSCHEMA

internal fun String.isLoginCallbackDeepLink(): Boolean =
    startsWith(
        prefix = "$APPSCHEMA://Callback/SignIn/",
        ignoreCase = true,
    ) ||
        startsWith(
            prefix = "pixiv://account/login",
            ignoreCase = true,
        ) ||
        startsWith(
            prefix = "https://app-api.pixiv.net/web/v1/users/auth/pixiv/callback",
            ignoreCase = true,
        )
