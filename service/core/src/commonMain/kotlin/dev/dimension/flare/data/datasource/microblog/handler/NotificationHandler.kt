package dev.dimension.flare.data.datasource.microblog.handler

import dev.dimension.flare.common.MemCacheable
import dev.dimension.flare.data.datasource.microblog.loader.NotificationLoader
import dev.dimension.flare.model.MicroBlogKey

internal class NotificationHandler(
    val accountKey: MicroBlogKey,
    val loader: NotificationLoader,
    private val fetchBadgeCount: (suspend () -> Int) = { loader.notificationBadgeCount() },
) {
    private val key by lazy {
        "notificationHandler_$accountKey"
    }

    val notificationBadgeCount by lazy {
        MemCacheable(
            key = key,
            fetchSource = {
                fetchBadgeCount.invoke()
            },
        )
    }

    fun update(count: Int) {
        MemCacheable.update(key, count)
    }

    fun clear() {
        update(0)
    }
}
