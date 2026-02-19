package dev.dimension.flare.ui.model

import kotlinx.serialization.Serializable

public data class ClickContext(
    val launcher: UriLauncher,
)

@Serializable
internal sealed interface ClickEvent {
    @Serializable
    data object Noop : ClickEvent

    @Serializable
    data class Deeplink(
        val url: String,
    ) : ClickEvent
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
