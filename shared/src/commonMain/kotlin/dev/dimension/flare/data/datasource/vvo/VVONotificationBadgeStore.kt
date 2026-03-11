package dev.dimension.flare.data.datasource.vvo

import dev.dimension.flare.data.datasource.microblog.NotificationFilter

internal class VVONotificationBadgeStore(
    private val loader: VVOLoader,
    private val onTotalChanged: (Int) -> Unit,
) {
    private var serverCounts: Map<NotificationFilter, Int> = emptyCounts()
    private var hydrated: Boolean = false
    private val localOverrides = mutableMapOf<NotificationFilter, Int>()

    suspend fun refreshAndGetTotal(): Int {
        serverCounts = loader.notificationBadgeCounts()
        hydrated = true
        localOverrides.clear()
        return currentTotal().also(onTotalChanged)
    }

    suspend fun clear(filter: NotificationFilter) {
        ensureHydrated()
        localOverrides[filter] = 0
        onTotalChanged(currentTotal())
    }

    suspend fun clearAll() {
        ensureHydrated()
        trackedFilters.forEach { localOverrides[it] = 0 }
        onTotalChanged(currentTotal())
    }

    private fun currentTotal(): Int = trackedFilters.sumOf { effectiveCount(it) }

    private fun effectiveCount(filter: NotificationFilter): Int = localOverrides[filter] ?: serverCounts[filter] ?: 0

    private suspend fun ensureHydrated() {
        if (!hydrated) {
            serverCounts = loader.notificationBadgeCounts()
            hydrated = true
        }
    }

    private companion object {
        val trackedFilters =
            listOf(
                NotificationFilter.Mention,
                NotificationFilter.Comment,
                NotificationFilter.Like,
            )

        fun emptyCounts(): Map<NotificationFilter, Int> = trackedFilters.associateWith { 0 }
    }
}
