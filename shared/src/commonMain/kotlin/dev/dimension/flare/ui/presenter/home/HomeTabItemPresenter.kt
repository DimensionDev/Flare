package dev.dimension.flare.ui.presenter.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.dimension.flare.data.model.tab.TimelineTabItemV2
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.ui.presenter.guestMastodonHomeTimelineTab
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapNotNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class HomeTabItemPresenter(
    private val id: String,
) : PresenterBase<HomeTabItemPresenter.State>(),
    KoinComponent {
    private val settingsRepository: SettingsRepository by inject()

    public interface State {
        public val tabItem: UiState<TimelineTabItemV2>
    }

    @Composable
    override fun body(): State {
        val tabItem by
            remember(id) {
                if (id == guestMastodonHomeTimelineTab.id) {
                    flowOf(guestMastodonHomeTimelineTab)
                } else {
                    settingsRepository.homeTimelineTab(id).mapNotNull { it }
                }
            }.collectAsUiState()

        return object : State {
            override val tabItem = tabItem
        }
    }
}
