package dev.dimension.flare.ui.presenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import dev.dimension.flare.data.model.tab.TimelineSlot
import dev.dimension.flare.data.model.tab.TimelineResolver
import dev.dimension.flare.data.model.tab.TimelineTabItemV2
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.map
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class PinTabs<T> internal constructor(
    private val slots: List<TimelineSlot>,
    private val matches: (TimelineSlot, T) -> Boolean,
) {
    public fun contains(item: T): Boolean = slots.any { matches(it, item) }
}

public abstract class PinTabsPresenter<T> :
    PresenterBase<PinTabsPresenter.State<T>>(),
    KoinComponent {
    private val settingsRepository by inject<SettingsRepository>()
    private val timelineResolver by inject<TimelineResolver>()
    private val appScope: CoroutineScope by inject()

    public interface State<T> {
        public val pins: UiState<PinTabs<T>>

        public fun pinTab(item: T)

        public fun unpinTab(item: T)

        public fun timelineTabItem(item: T): TimelineTabItemV2
    }

    @Composable
    override fun body(): State<T> {
        val tabSettings by settingsRepository.tabSettingsV2.collectAsUiState()
        val pins =
            tabSettings.map {
                PinTabs(it.homeSlots, ::matches)
            }

        return object : State<T> {
            override val pins = pins

            override fun pinTab(item: T) {
                appScope.launch {
                    settingsRepository.updateTabSettingsV2 {
                        if (homeSlots.any { matches(it, item) }) {
                            return@updateTabSettingsV2 this
                        }
                        copy(
                            homeSlots =
                                homeSlots + getTimelineTabItem(item),
                        )
                    }
                }
            }

            override fun unpinTab(item: T) {
                appScope.launch {
                    settingsRepository.updateTabSettingsV2 {
                        copy(
                            homeSlots = homeSlots.filterNot { matches(it, item) },
                        )
                    }
                }
            }

            override fun timelineTabItem(item: T): TimelineTabItemV2 =
                timelineResolver.toTabItem(getTimelineTabItem(item))
        }
    }

    protected abstract fun getTimelineTabItem(item: T): TimelineSlot

    protected open fun matches(
        slot: TimelineSlot,
        item: T,
    ): Boolean = slot.id == getTimelineTabItem(item).id
}
