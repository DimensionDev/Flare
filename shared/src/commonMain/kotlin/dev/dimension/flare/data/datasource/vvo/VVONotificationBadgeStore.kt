package dev.dimension.flare.data.datasource.vvo

import dev.dimension.flare.data.datasource.microblog.NotificationFilter

internal class VVONotificationBadgeStore(
    private val loader: VVOLoader,
    private val onTotalChanged: (Int) -> Unit,
) {
    private var serverCounts: Map<NotificationFilter, Int> = emptyCounts()
    private val localOverrides = mutableMapOf<NotificationFilter, Int>()

    suspend fun refreshAndGetTotal(): Int {
        serverCounts = loader.notificationBadgeCounts()
        localOverrides.clear()
        return currentTotal().also(onTotalChanged)
    }

    fun clear(filter: NotificationFilter) {
        localOverrides[filter] = 0
        onTotalChanged(currentTotal())
    }

    fun clearAll() {
        trackedFilters.forEach { localOverrides[it] = 0 }
        onTotalChanged(currentTotal())
    }

    private fun currentTotal(): Int = trackedFilters.sumOf { effectiveCount(it) }

    private fun effectiveCount(filter: NotificationFilter): Int = localOverrides[filter] ?: serverCounts[filter] ?: 0

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
