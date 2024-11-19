package dev.dimension.flare.ui.screen.settings

import dev.dimension.flare.ui.component.ComposeViewController

@Suppress("FunctionName")
fun AboutViewController(
    version: String,
    onOpenLink: (String) -> Unit,
    darkMode: Boolean,
) = ComposeViewController(
    onOpenLink = onOpenLink,
    darkMode = darkMode,
    // use secondary color when dark mode
    secondary = darkMode,
) {
    AboutScreenContent(version)
}
