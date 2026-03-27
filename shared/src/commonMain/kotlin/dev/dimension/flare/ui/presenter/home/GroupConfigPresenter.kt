package dev.dimension.flare.ui.presenter.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.MixedTimelineTabItem
import dev.dimension.flare.data.model.TabMetaData
import dev.dimension.flare.data.model.TabSettings
import dev.dimension.flare.data.model.TimelineTabItem
import dev.dimension.flare.data.model.TitleType
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.data.repository.sanitizeDuplicateTabKeys
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class GroupConfigPresenter :
    PresenterBase<GroupConfigPresenter.State>(),
    KoinComponent {
    private val settingsRepository: SettingsRepository by inject()
    private val appScope: CoroutineScope by inject()

    @Composable
    override fun body(): State {
        val availableIcons =
            remember {
                UiIcon.entries.map { IconType.Material(it) }.toImmutableList()
            }

        return object : State {
            override val availableIcons: ImmutableList<IconType> = availableIcons

            override fun commit(
                initialItem: MixedTimelineTabItem?,
                name: String,
                icon: IconType,
                tabs: List<TimelineTabItem>,
                defaultGroupName: String,
            ) {
                appScope.launch {
                    settingsRepository.updateTabSettings {
                        upsertGroupConfig(
                            initialItem = initialItem,
                            name = name,
                            icon = icon,
                            tabs = tabs,
                            defaultGroupName = defaultGroupName,
                        )
                    }
                }
            }
        }
    }

    @Immutable
    public interface State {
        public val availableIcons: ImmutableList<IconType>

        public fun commit(
            initialItem: MixedTimelineTabItem?,
            name: String,
            icon: IconType,
            tabs: List<TimelineTabItem>,
            defaultGroupName: String,
        )
    }
}

internal fun TabSettings.upsertGroupConfig(
    initialItem: MixedTimelineTabItem?,
    name: String,
    icon: IconType,
    tabs: List<TimelineTabItem>,
    defaultGroupName: String,
): TabSettings {
    val deduplicatedTabs = tabs.distinctBy { it.key }
    if (deduplicatedTabs.isEmpty()) {
        if (initialItem == null) {
            return this
        }
        val filteredTabs = mainTabs.filterNot { it.key == initialItem.key }
        return if (filteredTabs == mainTabs) {
            this
        } else {
            copy(mainTabs = filteredTabs).sanitizeDuplicateTabKeys()
        }
    }

    val newGroup =
        MixedTimelineTabItem(
            subTimelineTabItem = deduplicatedTabs,
            metaData =
                TabMetaData(
                    title = TitleType.Text(name.ifEmpty { defaultGroupName }),
                    icon = icon,
                ),
        )
    val currentTabs = mainTabs.toMutableList()
    val targetIndex =
        initialItem
            ?.let { item -> currentTabs.indexOfFirst { it.key == item.key } }
            ?.takeIf { it >= 0 }
            ?: currentTabs.size
    currentTabs.removeAll {
        it.key == newGroup.key || (initialItem != null && it.key == initialItem.key)
    }
    currentTabs.add(minOf(targetIndex, currentTabs.size), newGroup)
    return copy(mainTabs = currentTabs).sanitizeDuplicateTabKeys()
}
