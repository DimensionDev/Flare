package dev.dimension.flare.ui.presenter.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.appearance.AppearancePatch
import dev.dimension.flare.data.model.appearance.toBag
import dev.dimension.flare.data.model.tab.GroupSource
import dev.dimension.flare.data.model.tab.TabSettingsV2
import dev.dimension.flare.data.model.tab.TimelineFilterConfig
import dev.dimension.flare.data.model.tab.TimelineMergePolicy
import dev.dimension.flare.data.model.tab.TimelinePresentation
import dev.dimension.flare.data.model.tab.TimelineResolver
import dev.dimension.flare.data.model.tab.TimelineSlot
import dev.dimension.flare.data.model.tab.TimelineSlotContent
import dev.dimension.flare.data.model.tab.UiGroupTimelineTabItem
import dev.dimension.flare.data.model.tab.UiTimelineTabItem
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.ui.model.TabPickerUiIcons
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import dev.dimension.flare.di.koinInject

public class GroupConfigPresenter :
    PresenterBase<GroupConfigPresenter.State>() {
    private val settingsRepository: SettingsRepository by koinInject()
    private val appScope: CoroutineScope by koinInject()
    private val timelineResolver: TimelineResolver by koinInject()

    @Composable
    override fun body(): State {
        val availableIcons =
            remember {
                TabPickerUiIcons.map { IconType.Material(it) }.toImmutableList()
            }

        return object : State {
            override val availableIcons: ImmutableList<IconType> = availableIcons

            override fun buildGroupItem(
                initialItem: UiGroupTimelineTabItem?,
                name: String,
                icon: IconType,
                appearancePatch: AppearancePatch?,
                enabled: Boolean,
                tabs: List<UiTimelineTabItem>,
                mergePolicy: TimelineMergePolicy,
                filterConfig: TimelineFilterConfig,
                defaultGroupName: String,
            ): UiGroupTimelineTabItem? {
                val childSlots =
                    tabs
                        .distinctBy { it.id }
                        .map { timelineResolver.toSlot(it) }
                if (childSlots.isEmpty()) {
                    return null
                }
                return timelineResolver.toTabItem(
                    buildGroupSlot(
                        name = name,
                        icon = icon,
                        appearancePatch = appearancePatch,
                        enabled = enabled,
                        mergePolicy = mergePolicy,
                        filterConfig = filterConfig,
                        defaultGroupName = defaultGroupName,
                        childSlots = childSlots,
                    ),
                ) as UiGroupTimelineTabItem
            }

            override fun commit(
                initialItem: UiGroupTimelineTabItem?,
                name: String,
                icon: IconType,
                appearancePatch: AppearancePatch?,
                enabled: Boolean,
                tabs: List<UiTimelineTabItem>,
                mergePolicy: TimelineMergePolicy,
                filterConfig: TimelineFilterConfig,
                defaultGroupName: String,
            ) {
                appScope.launch {
                    settingsRepository.updateTabSettingsV2 {
                        upsertGroupConfig(
                            initialItem = initialItem,
                            name = name,
                            icon = icon,
                            appearancePatch = appearancePatch,
                            enabled = enabled,
                            tabs = tabs,
                            mergePolicy = mergePolicy,
                            filterConfig = filterConfig,
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

        public fun buildGroupItem(
            initialItem: UiGroupTimelineTabItem?,
            name: String,
            icon: IconType,
            appearancePatch: AppearancePatch?,
            enabled: Boolean,
            tabs: List<UiTimelineTabItem>,
            mergePolicy: TimelineMergePolicy = initialItem?.mergePolicy ?: TimelineMergePolicy.TimePerPage,
            filterConfig: TimelineFilterConfig = initialItem?.filterConfig ?: TimelineFilterConfig(),
            defaultGroupName: String,
        ): UiGroupTimelineTabItem?

        public fun commit(
            initialItem: UiGroupTimelineTabItem?,
            name: String,
            icon: IconType,
            appearancePatch: AppearancePatch?,
            enabled: Boolean,
            tabs: List<UiTimelineTabItem>,
            mergePolicy: TimelineMergePolicy = initialItem?.mergePolicy ?: TimelineMergePolicy.TimePerPage,
            filterConfig: TimelineFilterConfig = initialItem?.filterConfig ?: TimelineFilterConfig(),
            defaultGroupName: String,
        )
    }
}

internal fun TabSettingsV2.upsertGroupConfig(
    initialItem: UiGroupTimelineTabItem?,
    name: String,
    icon: IconType,
    appearancePatch: AppearancePatch?,
    enabled: Boolean,
    tabs: List<UiTimelineTabItem>,
    mergePolicy: TimelineMergePolicy = initialItem?.mergePolicy ?: TimelineMergePolicy.TimePerPage,
    filterConfig: TimelineFilterConfig = initialItem?.filterConfig ?: TimelineFilterConfig(),
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
    val newGroup = buildGroupSlot(name, icon, appearancePatch, enabled, mergePolicy, filterConfig, defaultGroupName, childSlots)
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
    appearancePatch: AppearancePatch?,
    enabled: Boolean,
    mergePolicy: TimelineMergePolicy,
    filterConfig: TimelineFilterConfig,
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
                mergePolicy = mergePolicy,
            ),
        presentation =
            TimelinePresentation(
                titleOverride = title,
                iconOverride = icon,
                appearanceOverride = appearancePatch?.takeUnless { it == AppearancePatch.EMPTY }?.toBag(),
                enabled = enabled,
                filterConfig = filterConfig,
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
