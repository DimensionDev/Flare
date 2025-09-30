package dev.dimension.flare.ui.controllers

import androidx.compose.foundation.rememberScrollState
import dev.dimension.flare.ui.component.ScrollToTopHandler
import dev.dimension.flare.ui.screen.settings.AboutScreenContent
import platform.UIKit.UIViewController

@Suppress("FunctionName")
public fun AboutScreenController(
    version: String,
    state: ComposeUIStateProxy<Unit>,
): UIViewController =
    FlareComposeUIViewController(state) { state ->
        val scrollState = rememberScrollState()
        ScrollToTopHandler(scrollState)
        AboutScreenContent(
            version = version,
            scrollState = scrollState,
        )
    }
