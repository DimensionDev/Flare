package dev.dimension.flare.ui.screen.rss

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.dimension.flare.data.model.RssTimelineTabItem
import dev.dimension.flare.data.model.TimelineTabItem
import dev.dimension.flare.ui.model.UiRssSource
import dev.dimension.flare.ui.presenter.PinTabsPresenter
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.ui.presenter.home.rss.RssSourcesPresenter
import dev.dimension.flare.ui.presenter.invoke

public class RssListWithTabsPresenter : PresenterBase<RssListWithTabsPresenter.State>() {
    private val pinTabsPresenter by lazy {
        object : PinTabsPresenter<UiRssSource>() {
            override fun List<TimelineTabItem>.filterPinned(): List<String> =
                filterIsInstance<RssTimelineTabItem>()
                    .map { it.feedUrl }

            override fun getTimelineTabItem(item: UiRssSource): TimelineTabItem = RssTimelineTabItem(item)

            override fun List<TimelineTabItem>.filter(item: UiRssSource): List<TimelineTabItem> =
                filterNot { it is RssTimelineTabItem && it.feedUrl == item.url }
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

    public interface State :
        RssSourcesPresenter.State,
        PinTabsPresenter.State<UiRssSource>
}
