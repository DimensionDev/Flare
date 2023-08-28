package dev.dimension.flare.data.repository.cache

import com.moriatsushi.koject.inject
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.model.StatusContent
import dev.dimension.flare.model.MicroBlogKey

internal suspend inline fun <reified T : StatusContent> updateStatusUseCase(
    statusKey: MicroBlogKey,
    accountKey: MicroBlogKey,
    update: (content: T) -> T,
    cacheDatabase: CacheDatabase = inject(),
) {
    val status = cacheDatabase.statusDao().getStatus(statusKey, accountKey)
    if (status != null && status.content is T) {
        cacheDatabase.statusDao().updateStatus(statusKey, accountKey, update(status.content))
    }
}
