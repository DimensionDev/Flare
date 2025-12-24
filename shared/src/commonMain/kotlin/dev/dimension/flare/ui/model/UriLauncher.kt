package dev.dimension.flare.ui.model

import dev.dimension.flare.ui.route.DeeplinkRoute
import dev.dimension.flare.ui.route.toUri

public fun interface UriLauncher {
    public fun launch(uri: String)
}

public fun UriLauncher.launch(route: DeeplinkRoute) {
    launch(route.toUri())
}
