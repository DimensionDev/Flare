package dev.dimension.flare.ui.common

import coil3.PlatformContext

internal actual object PlatformShare {
    actual fun shareText(
        context: PlatformContext,
        text: String,
    ) {
        // Android implementation
        val intent =
            android.content.Intent().apply {
                action = android.content.Intent.ACTION_SEND
                putExtra(android.content.Intent.EXTRA_TEXT, text)
                type = "text/plain"
            }
        context.startActivity(
            android.content.Intent.createChooser(
                intent,
                null,
            ),
        )
    }
}
