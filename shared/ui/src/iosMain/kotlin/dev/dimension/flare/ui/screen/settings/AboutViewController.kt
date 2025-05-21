package dev.dimension.flare.ui.screen.settings

import androidx.compose.ui.graphics.Color
import dev.dimension.flare.ui.component.ComposeViewController
import platform.UIKit.UIViewController

@Suppress("FunctionName")
public fun AboutViewController(
    version: String,
    onOpenLink: (String) -> Unit,
    darkMode: Boolean,
    backgroundColorValue: ULong,
): UIViewController =
    ComposeViewController(
        onOpenLink = onOpenLink,
        darkMode = darkMode,
        // use secondary color when dark mode
        secondary = darkMode,
        backgroundColor = createColorFromULong(backgroundColorValue),
    ) {
        AboutScreenContent(version)
    }

private fun createColorFromULong(value: ULong): Color {
    val alpha = ((value shr 24) and 0xFFUL).toFloat() / 255f
    val red = ((value shr 16) and 0xFFUL).toFloat() / 255f
    val green = ((value shr 8) and 0xFFUL).toFloat() / 255f
    val blue = (value and 0xFFUL).toFloat() / 255f
    return Color(red, green, blue, alpha)
}
