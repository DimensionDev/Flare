package dev.dimension.flare.ui.common

import coil3.PlatformContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

internal actual object PlatformShare : KoinComponent {
    val desktopShare: DesktopShare by inject()

    actual fun shareText(
        context: PlatformContext,
        text: String,
    ) {
        desktopShare.shareText(text)
    }
}

public interface DesktopShare {
    public fun shareText(text: String)
}
