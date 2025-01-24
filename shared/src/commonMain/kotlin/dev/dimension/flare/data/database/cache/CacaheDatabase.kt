package dev.dimension.flare.data.database.cache

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters

internal const val CACHE_DATABASE_VERSION = 16

@Database(
    entities = [
        dev.dimension.flare.data.database.cache.model.DbEmoji::class,
        dev.dimension.flare.data.database.cache.model.DbStatusReference::class,
        dev.dimension.flare.data.database.cache.model.DbStatus::class,
        dev.dimension.flare.data.database.cache.model.DbUser::class,
        dev.dimension.flare.data.database.cache.model.DbPagingTimeline::class,
        dev.dimension.flare.data.database.cache.model.DbMessageRoom::class,
        dev.dimension.flare.data.database.cache.model.DbMessageItem::class,
        dev.dimension.flare.data.database.cache.model.DbDirectMessageTimeline::class,
        dev.dimension.flare.data.database.cache.model.DbMessageRoomReference::class,
        dev.dimension.flare.data.database.cache.model.DbUserHistory::class,
    ],
    version = CACHE_DATABASE_VERSION,
    exportSchema = false,
)
@TypeConverters(
    dev.dimension.flare.data.database.adapter.MicroBlogKeyConverter::class,
    dev.dimension.flare.data.database.adapter.PlatformTypeConverter::class,
    dev.dimension.flare.data.database.cache.model.EmojiContentConverter::class,
    dev.dimension.flare.data.database.cache.model.StatusConverter::class,
    dev.dimension.flare.data.database.cache.model.UserContentConverters::class,
    dev.dimension.flare.data.database.cache.model.MessageContentConverters::class,
)
@ConstructedBy(CacheDatabaseConstructor::class)
internal abstract class CacheDatabase : RoomDatabase() {
    abstract fun emojiDao(): dev.dimension.flare.data.database.cache.dao.EmojiDao

    abstract fun statusReferenceDao(): dev.dimension.flare.data.database.cache.dao.StatusReferenceDao

    abstract fun statusDao(): dev.dimension.flare.data.database.cache.dao.StatusDao

    abstract fun userDao(): dev.dimension.flare.data.database.cache.dao.UserDao

    abstract fun pagingTimelineDao(): dev.dimension.flare.data.database.cache.dao.PagingTimelineDao

    abstract fun messageDao(): dev.dimension.flare.data.database.cache.dao.MessageDao
}

// The Room compiler generates the `actual` implementations.
@Suppress("NO_ACTUAL_FOR_EXPECT")
internal expect object CacheDatabaseConstructor : RoomDatabaseConstructor<CacheDatabase> {
    override fun initialize(): CacheDatabase
}
