package dev.dimension.flare.ui.model

import dev.dimension.flare.ui.route.DeeplinkRoute
import dev.dimension.flare.ui.route.toUri
import kotlinx.serialization.Serializable

public data class ClickContext(
    val launcher: UriLauncher,
)

@Serializable
internal sealed interface ClickEvent {
    @Serializable
    data object Noop : ClickEvent

    @Serializable
    data class Deeplink private constructor(
        val url: String,
    ) : ClickEvent {
        constructor(route: DeeplinkRoute) : this(route.toUri())
    }
}

internal val ClickEvent.onClicked: ClickContext.() -> Unit
    get() {
        when (this) {
            is ClickEvent.Deeplink -> {
                return {
                    launcher.launch(url)
                }
            }

            is ClickEvent.Noop -> {
                return {}
            }
        }
    }
