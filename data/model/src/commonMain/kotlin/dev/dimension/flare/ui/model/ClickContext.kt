package dev.dimension.flare.ui.model

import dev.dimension.flare.data.datasource.microblog.StatusMutation
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.route.DeeplinkRoute
import dev.dimension.flare.ui.route.toUri
import kotlinx.serialization.Serializable

public data class ClickContext(
    val launcher: UriLauncher,
)

@Serializable
public sealed interface ClickEvent {
    @Serializable
    public data object Noop : ClickEvent

    @Serializable
    public data class Deeplink(
        val url: String,
    ) : ClickEvent {
        public constructor(route: DeeplinkRoute) : this(route.toUri())
        public constructor(route: DeeplinkEvent) : this(route.toUri())
    }

    public companion object {
        public fun mutation(
            statusMutation: StatusMutation,
        ): ClickEvent = Deeplink(
            DeeplinkEvent(
                accountKey = statusMutation.accountKey,
                statusMutation = statusMutation,
            ),
        )

        public fun mutation(
            accountKey: MicroBlogKey?,
            statusKey: MicroBlogKey,
            type: String,
            params: Map<String, String> = emptyMap(),
        ): ClickEvent = if (accountKey == null) {
            Noop
        } else {
            mutation(
                StatusMutation(
                    statusKey = statusKey,
                    accountKey = accountKey,
                    type = type,
                    params = params,
                ),
            )
        }
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
