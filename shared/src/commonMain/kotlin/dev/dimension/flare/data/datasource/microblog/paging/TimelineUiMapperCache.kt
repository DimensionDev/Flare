package dev.dimension.flare.data.datasource.microblog.paging

import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.database.cache.model.TranslationDisplayOptions
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Bounded LRU for the expensive database-model to UI-model conversion. */
internal class TimelineUiMapperCache(
    private val maxSize: Int = DEFAULT_MAX_SIZE,
) {
    private val mutex = Mutex()
    private val entries = LinkedHashMap<Key, Entry>()

    init {
        require(maxSize > 0) { "maxSize must be positive" }
    }

    suspend fun toUi(
        item: DbPagingTimelineWithStatus,
        pagingKey: String,
        translationDisplayOptions: TranslationDisplayOptions,
    ): UiTimelineV2 =
        mutex.withLock {
            val key = Key(pagingKey = pagingKey, timelineId = item.timeline._id)
            val cached = entries.remove(key)
            if (cached != null && cached.item === item && cached.translationDisplayOptions == translationDisplayOptions) {
                entries[key] = cached
                return@withLock cached.ui
            }

            val ui =
                TimelinePagingMapper.toUi(
                    item = item,
                    pagingKey = pagingKey,
                    translationDisplayOptions = translationDisplayOptions,
                )
            entries[key] =
                Entry(
                    item = item,
                    translationDisplayOptions = translationDisplayOptions,
                    ui = ui,
                )
            if (entries.size > maxSize) {
                entries.remove(entries.keys.first())
            }
            ui
        }

    private data class Key(
        val pagingKey: String,
        val timelineId: String,
    )

    private data class Entry(
        val item: DbPagingTimelineWithStatus,
        val translationDisplayOptions: TranslationDisplayOptions,
        val ui: UiTimelineV2,
    )

    private companion object {
        const val DEFAULT_MAX_SIZE = 1024
    }
}
