package dev.dimension.flare.ui.presenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.dimension.flare.data.model.tab.TimelineResolver
import dev.dimension.flare.data.model.tab.UiTimelineItem
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.presenter.home.ActiveAccountPresenter
import dev.dimension.flare.ui.presenter.home.UserState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class HomeTimelineWithTabsPresenter :
    PresenterBase<HomeTimelineWithTabsPresenter.State>(),
    KoinComponent {
    private val resolver: TimelineResolver by inject()
    private val settingsRepository by inject<SettingsRepository>()

    public interface State : UserState {
        public val tabState: UiState<ImmutableList<UiTimelineItem>>
    }

    private val tabsState by lazy {
        settingsRepository.tabSettingsV2
            .distinctUntilChangedBy { it.homeSlots }
            .map {
                it.homeSlots.map {
                    resolver.toUi(it)
                }.toImmutableList()
            }
    }

    @Composable
    override fun body(): State {
        val accountState =
            remember {
                ActiveAccountPresenter()
            }.body()

        val tabs by tabsState.collectAsUiState()

        return object : State, UserState by accountState {
            override val tabState = tabs
        }
    }
}
