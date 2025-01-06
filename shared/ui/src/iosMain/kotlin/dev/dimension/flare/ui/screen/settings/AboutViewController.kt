package dev.dimension.flare.ui.screen.settings

import dev.dimension.flare.ui.component.ComposeViewController
import platform.UIKit.UIViewController

@Suppress("FunctionName")
public fun AboutViewController(
    version: String,
    onOpenLink: (String) -> Unit,
    darkMode: Boolean,
): UIViewController =
    ComposeViewController(
        onOpenLink = onOpenLink,
        darkMode = darkMode,
        // use secondary color when dark mode
        secondary = darkMode,
    ) {
        AboutScreenContent(version)
    }
