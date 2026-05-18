package dev.dimension.flare.data.repository

import dev.dimension.flare.data.model.tab.TimelineResolver
import dev.dimension.flare.data.model.tab.TimelineTabItemV2
import dev.dimension.flare.data.model.tab.findById
import dev.dimension.flare.data.model.tab.isSystemHomeMixedTimeline
import dev.dimension.flare.data.model.tab.withSystemHomeMixedTimelineEnabled
import dev.dimension.flare.model.defaultSocialPlatformRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map

private val defaultTimelineResolver by lazy { TimelineResolver(defaultSocialPlatformRegistry) }

public val SettingsRepository.homeTimelineTabs: Flow<List<TimelineTabItemV2>>
    get() = homeTimelineTabs(defaultTimelineResolver)

internal fun SettingsRepository.homeTimelineTabs(timelineResolver: TimelineResolver): Flow<List<TimelineTabItemV2>> =
    tabSettingsV2
        .distinctUntilChangedBy { it.homeSlots }
        .map { settings ->
            val tabs =
                settings.homeSlots
                    .map { timelineResolver.toTabItem(it) }
            tabs.withSystemHomeMixedTimelineEnabled(
                enabled = tabs.any { it.isSystemHomeMixedTimeline },
            )
        }

internal fun SettingsRepository.homeTimelineTab(id: String): Flow<TimelineTabItemV2?> =
    homeTimelineTabs.map { tabs ->
        tabs.findById(id)
    }

internal suspend fun SettingsRepository.replaceHomeTimelineTabs(tabs: List<TimelineTabItemV2>) {
    updateTabSettingsV2 {
        val normalizedTabs =
            tabs.withSystemHomeMixedTimelineEnabled(
                enabled = tabs.any { it.isSystemHomeMixedTimeline },
            )
        copy(
            homeSlots =
                normalizedTabs
                    .distinctBy { it.id }
                    .map { defaultTimelineResolver.toSlot(it) },
        )
    }
}
