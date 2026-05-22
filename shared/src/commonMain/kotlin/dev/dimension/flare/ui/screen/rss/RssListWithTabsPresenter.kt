package dev.dimension.flare.ui.screen.rss

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import dev.dimension.flare.data.database.app.model.SubscriptionType
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.tab.RssTimelineData
import dev.dimension.flare.data.model.tab.SubscriptionTimelineData
import dev.dimension.flare.data.model.tab.TimelineSlot
import dev.dimension.flare.data.model.tab.toSlot
import dev.dimension.flare.data.platform.RssTimelineSpecs
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiRssSource
import dev.dimension.flare.ui.model.UiText
import dev.dimension.flare.ui.presenter.PinTabsPresenter
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.ui.presenter.home.rss.RssSourcesPresenter
import dev.dimension.flare.ui.presenter.invoke

public class RssListWithTabsPresenter : PresenterBase<RssListWithTabsPresenter.State>() {
    private val pinTabsPresenter by lazy {
        object : PinTabsPresenter<UiRssSource>() {
            override fun getTimelineTabItem(item: UiRssSource): TimelineSlot =
                if (item.type == SubscriptionType.RSS) {
                    RssTimelineSpecs.rss
                        .target(
                            data = RssTimelineData(item.url),
                            title = UiText.Raw(item.title ?: item.url),
                            icon = item.favIcon?.let { IconType.Url(it) } ?: IconType.Material(UiIcon.Rss),
                        ).toSlot()
                } else {
                    RssTimelineSpecs.subscription
                        .target(
                            data =
                                SubscriptionTimelineData(
                                    subscriptionUrl = item.url,
                                    subscriptionType = item.type,
                                ),
                            title = UiText.Raw(item.title ?: item.url),
                            icon =
                                item.favIcon?.let { IconType.Url(it) }
                                    ?: if (item.type == SubscriptionType.RSS) {
                                        IconType.Material(UiIcon.Rss)
                                    } else {
                                        IconType.FavIcon(item.host)
                                    },
                        ).toSlot()
                }
        }
    }

    @Composable
    override fun body(): State {
        val state = remember { RssSourcesPresenter() }.invoke()
        val pinState = pinTabsPresenter.invoke()
        return object :
            State,
            RssSourcesPresenter.State by state,
            PinTabsPresenter.State<UiRssSource> by pinState {
        }
    }

    @Immutable
    public interface State :
        RssSourcesPresenter.State,
        PinTabsPresenter.State<UiRssSource>
}
