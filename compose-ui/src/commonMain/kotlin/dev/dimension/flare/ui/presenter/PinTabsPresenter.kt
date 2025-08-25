package dev.dimension.flare.ui.presenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import dev.dimension.flare.data.model.TimelineTabItem
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.map
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public abstract class PinTabsPresenter<T> :
    PresenterBase<PinTabsPresenter.State<T>>(),
    KoinComponent {
    private val settingsRepository by inject<SettingsRepository>()
    private val appScope: CoroutineScope by inject()

    public interface State<T> {
        public val currentTabs: UiState<ImmutableList<String>>

        public fun pinTab(item: T)

        public fun unpinTab(item: T)
    }

    @Composable
    override fun body(): State<T> {
        val tabSettings by settingsRepository.tabSettings.collectAsUiState()
        val currentTabs =
            tabSettings.map {
                it.mainTabs
                    .filterPinned()
                    .toImmutableList()
            }

        return object : State<T> {
            override val currentTabs = currentTabs

            override fun pinTab(item: T) {
                appScope.launch {
                    settingsRepository.updateTabSettings {
                        copy(
                            mainTabs =
                                mainTabs + getTimelineTabItem(item),
                        )
                    }
                }
            }

            override fun unpinTab(item: T) {
                appScope.launch {
                    settingsRepository.updateTabSettings {
                        copy(
                            mainTabs = mainTabs.filter(item),
                        )
                    }
                }
            }
        }
    }

    protected abstract fun List<TimelineTabItem>.filterPinned(): List<String>

    protected abstract fun getTimelineTabItem(item: T): TimelineTabItem

    protected abstract fun List<TimelineTabItem>.filter(item: T): List<TimelineTabItem>
}
