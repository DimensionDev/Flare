package dev.dimension.flare.data.repository

import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.model.tab.TimelinePersistenceMapper
import dev.dimension.flare.data.model.tab.TimelineTabItemV2
import dev.dimension.flare.data.model.tab.findById
import dev.dimension.flare.data.model.tab.isSystemHomeMixedTimeline
import dev.dimension.flare.data.model.tab.withSystemHomeMixedTimelineEnabled
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map

internal fun AppDataStore.homeTimelineTabs(timelinePersistenceMapper: TimelinePersistenceMapper): Flow<List<TimelineTabItemV2>> =
    tabSettingsV2
        .distinctUntilChangedBy { it.homeSlots }
        .map { settings ->
            val tabs =
                settings.homeSlots
                    .map { timelinePersistenceMapper.toTabItem(it) }
            tabs.withSystemHomeMixedTimelineEnabled(
                enabled = tabs.any { it.isSystemHomeMixedTimeline },
            )
        }

internal fun AppDataStore.homeTimelineTab(
    id: String,
    timelinePersistenceMapper: TimelinePersistenceMapper,
): Flow<TimelineTabItemV2?> =
    homeTimelineTabs(timelinePersistenceMapper).map { tabs ->
        tabs.findById(id)
    }

internal suspend fun AppDataStore.replaceHomeTimelineTabs(
    tabs: List<TimelineTabItemV2>,
    timelinePersistenceMapper: TimelinePersistenceMapper,
) {
    updateTabSettingsV2 {
        val normalizedTabs =
            tabs.withSystemHomeMixedTimelineEnabled(
                enabled = tabs.any { it.isSystemHomeMixedTimeline },
            )
        copy(
            homeSlots =
                normalizedTabs
                    .distinctBy { it.id }
                    .map { timelinePersistenceMapper.toSlot(it) },
        )
    }
}
