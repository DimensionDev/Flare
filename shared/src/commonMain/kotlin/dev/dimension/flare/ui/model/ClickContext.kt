package dev.dimension.flare.ui.model

import dev.dimension.flare.data.datasource.microblog.PostEvent
import dev.dimension.flare.model.MicroBlogKey
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
        constructor(route: DeeplinkEvent) : this(route.toUri())
    }

    companion object {
        fun event(
            accountKey: MicroBlogKey?,
            postEvent: PostEvent,
        ) = if (accountKey == null) {
            Noop
        } else {
            Deeplink(
                DeeplinkEvent(
                    accountKey = accountKey,
                    postEvent = postEvent,
                ),
            )
        }
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
