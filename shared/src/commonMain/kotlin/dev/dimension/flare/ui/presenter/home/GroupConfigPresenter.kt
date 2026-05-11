package dev.dimension.flare.ui.presenter.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.tab.GroupSource
import dev.dimension.flare.data.model.tab.GroupTimelineTabItemV2
import dev.dimension.flare.data.model.tab.TabSettingsV2
import dev.dimension.flare.data.model.tab.TimelineMergePolicy
import dev.dimension.flare.data.model.tab.TimelinePresentation
import dev.dimension.flare.data.model.tab.TimelineResolver
import dev.dimension.flare.data.model.tab.TimelineSlot
import dev.dimension.flare.data.model.tab.TimelineSlotContent
import dev.dimension.flare.data.model.tab.TimelineTabItemV2
import dev.dimension.flare.data.repository.SettingsRepository
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
    private val timelineResolver: TimelineResolver by inject()

    @Composable
    override fun body(): State {
        val availableIcons =
            remember {
                UiIcon.entries.map { IconType.Material(it) }.toImmutableList()
            }

        return object : State {
            override val availableIcons: ImmutableList<IconType> = availableIcons

            override fun commit(
                initialItem: GroupTimelineTabItemV2?,
                name: String,
                icon: IconType,
                tabs: List<TimelineTabItemV2>,
                defaultGroupName: String,
            ) {
                appScope.launch {
                    settingsRepository.updateTabSettingsV2 {
                        upsertGroupConfig(
                            initialItem = initialItem,
                            name = name,
                            icon = icon,
                            tabs = tabs,
                            defaultGroupName = defaultGroupName,
                            timelineResolver = timelineResolver,
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
            initialItem: GroupTimelineTabItemV2?,
            name: String,
            icon: IconType,
            tabs: List<TimelineTabItemV2>,
            defaultGroupName: String,
        )
    }
}

internal fun TabSettingsV2.upsertGroupConfig(
    initialItem: GroupTimelineTabItemV2?,
    name: String,
    icon: IconType,
    tabs: List<TimelineTabItemV2>,
    defaultGroupName: String,
    timelineResolver: TimelineResolver,
): TabSettingsV2 {
    val deduplicatedTabs = tabs.distinctBy { it.id }
    if (deduplicatedTabs.isEmpty()) {
        if (initialItem == null) {
            return this
        }
        val filteredSlots = homeSlots.filterNot { it.id == initialItem.id }
        return if (filteredSlots == homeSlots) {
            this
        } else {
            copy(homeSlots = filteredSlots.sanitizeDuplicateSlotKeys())
        }
    }

    val childSlots = deduplicatedTabs.map { timelineResolver.toSlot(it) }
    val newGroup = buildGroupSlot(name, icon, defaultGroupName, childSlots)
    val currentSlots = homeSlots.toMutableList()
    val targetIndex =
        initialItem
            ?.let { item -> currentSlots.indexOfFirst { it.id == item.id } }
            ?.takeIf { it >= 0 }
            ?: currentSlots.size
    currentSlots.removeAll {
        it.id == newGroup.id || (initialItem != null && it.id == initialItem.id)
    }
    currentSlots.add(minOf(targetIndex, currentSlots.size), newGroup)
    return copy(homeSlots = currentSlots.sanitizeDuplicateSlotKeys())
}

private fun buildGroupSlot(
    name: String,
    icon: IconType,
    defaultGroupName: String,
    childSlots: List<TimelineSlot>,
): TimelineSlot {
    val title = name.ifEmpty { defaultGroupName }
    return TimelineSlot(
        id = buildGroupSlotId(title, childSlots),
        content =
            TimelineSlotContent.Group(
                children = childSlots,
                source = GroupSource.Manual,
                mergePolicy = TimelineMergePolicy.TimePerPage,
            ),
        presentation =
            TimelinePresentation(
                titleOverride = title,
                iconOverride = icon,
            ),
    )
}

private fun buildGroupSlotId(
    title: String,
    children: List<TimelineSlot>,
): String =
    buildString {
        append("mixed_timeline")
        append(title)
        children.forEach { append(it.id) }
    }

private fun List<TimelineSlot>.sanitizeDuplicateSlotKeys(): List<TimelineSlot> =
    mapNotNull { it.sanitizeDuplicateSlotKeys() }
        .distinctBy { it.id }

private fun TimelineSlot.sanitizeDuplicateSlotKeys(): TimelineSlot? =
    when (val slotContent = content) {
        is TimelineSlotContent.Group -> {
            val sanitizedChildren = slotContent.children.sanitizeDuplicateSlotKeys()
            if (sanitizedChildren.isEmpty()) {
                null
            } else if (sanitizedChildren == slotContent.children) {
                this
            } else {
                copy(content = slotContent.copy(children = sanitizedChildren))
            }
        }

        else -> {
            this
        }
    }
