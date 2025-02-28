package dev.dimension.flare.ui.route

import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey

internal sealed interface WindowRoute {
    data class RawImage(
        val url: String,
    ) : WindowRoute

    data class StatusMedia(
        val accountType: AccountType,
        val statusKey: MicroBlogKey,
        val index: Int,
    ) : WindowRoute
}

internal data class FloatingWindowState(
    val route: WindowRoute,
) {
    var bringToFront: () -> Unit = {}
}
