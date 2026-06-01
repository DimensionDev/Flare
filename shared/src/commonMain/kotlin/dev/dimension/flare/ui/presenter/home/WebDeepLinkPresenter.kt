package dev.dimension.flare.ui.presenter.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.dimension.flare.ui.model.ClickContext
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UriLauncher
import dev.dimension.flare.ui.model.onClicked
import dev.dimension.flare.ui.presenter.PresenterBase
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
                        onRoute(route.toUri())
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
