package dev.dimension.flare.ui.presenter.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import dev.dimension.flare.data.model.tab.TimelineResolver
import dev.dimension.flare.data.model.tab.TimelineTabItemV2
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.data.repository.homeTimelineTabs
import dev.dimension.flare.data.repository.replaceHomeTimelineTabs
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class HomeTabSettingsPresenter :
    PresenterBase<HomeTabSettingsPresenter.State>(),
    KoinComponent {
    private val settingsRepository: SettingsRepository by inject()
    private val timelineResolver: TimelineResolver by inject()
    private val appScope: CoroutineScope by inject()

    private val homeTimelineTabs by lazy {
        settingsRepository.homeTimelineTabs(timelineResolver)
            .map { it.toImmutableList() }
    }

    @Composable
    override fun body(): State {
        val tabs by homeTimelineTabs.collectAsUiState()

        return object : State {
            override val homeTimelineTabs: UiState<ImmutableList<TimelineTabItemV2>> = tabs

            override fun replaceHomeTimelineTabs(tabs: List<TimelineTabItemV2>) {
                appScope.launch {
                    settingsRepository.replaceHomeTimelineTabs(tabs, timelineResolver)
                }
            }
        }
    }

    public interface State {
        public val homeTimelineTabs: UiState<ImmutableList<TimelineTabItemV2>>

        public fun replaceHomeTimelineTabs(tabs: List<TimelineTabItemV2>)
    }
}
