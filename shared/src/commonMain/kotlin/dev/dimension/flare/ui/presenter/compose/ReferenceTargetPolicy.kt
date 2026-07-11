package dev.dimension.flare.ui.presenter.compose

import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.UiAccount

internal fun requiresReferenceShareImage(
    sourcePlatform: PlatformType?,
    sourceAccountKey: MicroBlogKey?,
    targetAccount: UiAccount,
): Boolean {
    sourcePlatform ?: return false
    if (targetAccount.platformType != sourcePlatform) {
        return true
    }
    return when (sourcePlatform) {
        PlatformType.Mastodon,
        PlatformType.Misskey,
        -> {
            sourceAccountKey == null ||
                !targetAccount.accountKey.host.equals(sourceAccountKey.host, ignoreCase = true)
        }

        else -> {
            false
        }
    }
}
