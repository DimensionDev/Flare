package dev.dimension.flare.ui.model

import dev.dimension.flare.data.datasource.microblog.PostEvent
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.route.DeeplinkRoute
import dev.dimension.flare.ui.route.toUri
import kotlinx.serialization.Serializable

public data class ClickContext(
    val launcher: UriLauncher,
)

public data class ClickPostEvent(
    val accountKey: MicroBlogKey,
    val postEvent: PostEvent,
)

@Serializable
public sealed interface ClickEvent {
    @Serializable
    public data object Noop : ClickEvent

    @Serializable
    public data class Deeplink public constructor(
        val url: String,
    ) : ClickEvent {
        public constructor(route: DeeplinkRoute) : this(route.toUri())
        internal constructor(route: DeeplinkEvent) : this(route.toUri())
    }

    public companion object {
        public fun event(
            accountKey: MicroBlogKey?,
            postEvent: PostEvent,
        ): ClickEvent =
            if (accountKey == null) {
                Noop
            } else {
                Deeplink(
                    DeeplinkEvent(
                        accountKey = accountKey,
                        postEvent = postEvent,
                    ),
                )
            }

        public fun event(
            accountKey: MicroBlogKey?,
            eventCreator: (accountKey: MicroBlogKey) -> PostEvent,
        ): ClickEvent =
            if (accountKey == null) {
                Noop
            } else {
                Deeplink(
                    DeeplinkEvent(
                        accountKey = accountKey,
                        postEvent = eventCreator.invoke(accountKey),
                    ),
                )
            }
    }
}

public fun ClickEvent.postEventOrNull(): ClickPostEvent? =
    when (this) {
        is ClickEvent.Deeplink -> {
            DeeplinkEvent
                .parse(url)
                ?.let { event ->
                    event.postEvent?.let { postEvent ->
                        ClickPostEvent(
                            accountKey = event.accountKey,
                            postEvent = postEvent,
                        )
                    }
                }
        }

        ClickEvent.Noop -> {
            null
        }
    }

public val ClickEvent.onClicked: ClickContext.() -> Unit
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
