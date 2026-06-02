package dev.dimension.flare.ui.presenter.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.ClickContext
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UriLauncher
import dev.dimension.flare.ui.model.onClicked
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.ui.route.DeeplinkRoute
import dev.dimension.flare.ui.route.toUri
import dev.dimension.flare.web.shared.WebPresenter

@WebPresenter("deepLink")
public class WebDeepLinkPresenter(
    private val onRoute: (String) -> Unit,
    private val onLink: (String) -> Unit,
) : PresenterBase<WebDeepLinkPresenter.State>() {
    public interface State {
        public fun handle(url: String)

        public fun performClickEvent(clickEvent: ClickEvent)
    }

    @Composable
    override fun body(): State {
        val deepLinkState =
            remember {
                DeepLinkPresenter(
                    onRoute = { route ->
                        onRoute(route.toWebPath() ?: route.toUri())
                    },
                    onLink = onLink,
                )
            }.body()

        return object : State {
            override fun handle(url: String) {
                deepLinkState.handle(url)
            }

            override fun performClickEvent(clickEvent: ClickEvent) {
                clickEvent.onClicked.invoke(clickContext(deepLinkState))
            }
        }
    }

    private fun clickContext(deepLinkState: DeepLinkPresenter.State): ClickContext =
        ClickContext(
            launcher =
                UriLauncher { url ->
                    deepLinkState.handle(url)
                },
        )
}

private fun DeeplinkRoute.toWebPath(): String? =
    when (this) {
        is DeeplinkRoute.Profile.User ->
            "/${accountType.toWebAccountSegment()}/profile/${userKey.toWebPathSegment()}"
        is DeeplinkRoute.Profile.UserNameWithHost ->
            "/${accountType.toWebAccountSegment()}/profile/by-handle/${host.toWebPathSegment()}/${userName.toWebPathSegment()}"
        else -> null
    }

private fun AccountType.toWebAccountSegment(): String =
    when (this) {
        is AccountType.Specific -> accountKey.toWebPathSegment()
        AccountType.Guest -> "guest"
        is AccountType.GuestHost -> "guest@$host".toWebPathSegment()
    }

private fun MicroBlogKey.toWebPathSegment(): String = toString().toWebPathSegment()

private fun String.toWebPathSegment(): String =
    buildString {
        for (byte in encodeToByteArray()) {
            val value = byte.toInt() and 0xff
            val char = value.toChar()
            if (char.isWebPathSegmentSafe()) {
                append(char)
            } else {
                append('%')
                append(value.toString(16).uppercase().padStart(2, '0'))
            }
        }
    }

private fun Char.isWebPathSegmentSafe(): Boolean =
    this in 'a'..'z' ||
        this in 'A'..'Z' ||
        this in '0'..'9' ||
        this == '-' ||
        this == '_' ||
        this == '.' ||
        this == '~' ||
        this == '@'
