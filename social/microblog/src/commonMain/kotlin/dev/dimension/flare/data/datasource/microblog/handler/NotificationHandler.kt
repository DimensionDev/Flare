package dev.dimension.flare.data.datasource.microblog.handler

import dev.dimension.flare.common.MemCacheable
import dev.dimension.flare.data.datasource.microblog.loader.NotificationLoader
import dev.dimension.flare.model.MicroBlogKey

public class NotificationHandler(
    public val accountKey: MicroBlogKey,
    public val loader: NotificationLoader,
    private val fetchBadgeCount: (suspend () -> Int) = { loader.notificationBadgeCount() },
) {
    private val key by lazy {
        "notificationHandler_$accountKey"
    }

    public val notificationBadgeCount: MemCacheable<Int> by lazy {
        MemCacheable(
            key = key,
            fetchSource = {
                fetchBadgeCount.invoke()
            },
        )
    }

    public fun update(count: Int) {
        MemCacheable.update(key, count)
    }

    public fun clear() {
        update(0)
    }
}
