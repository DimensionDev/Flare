package dev.dimension.flare.ui.presenter.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.dimension.flare.data.model.tab.UiTimelineTabItem
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.flattenUiState
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.ui.presenter.guestMastodonHomeTimelineTab
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import dev.dimension.flare.di.koinInject

public class HomeTabItemPresenter(
    private val id: String,
) : PresenterBase<HomeTabItemPresenter.State>() {
    private val settingsRepository: SettingsRepository by koinInject()

    public interface State {
        public val tabItem: UiState<UiTimelineTabItem>
    }

    @Composable
    override fun body(): State {
        val tabItem by
            remember(id) {
                if (id == guestMastodonHomeTimelineTab.id) {
                    flowOf(UiState.Success(guestMastodonHomeTimelineTab))
                } else {
                    settingsRepository.homeTimelineTab(id).map {
                        if (it == null) {
                            UiState.Error<UiTimelineTabItem>(Exception("Tab not found"))
                        } else {
                            UiState.Success(it)
                        }
                    }
                }
            }.flattenUiState()

        return object : State {
            override val tabItem = tabItem
        }
    }
}
