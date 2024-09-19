package dev.dimension.flare.data.database.cache

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters

@Database(
    entities = [
        dev.dimension.flare.data.database.cache.model.DbEmoji::class,
        dev.dimension.flare.data.database.cache.model.DbStatusReference::class,
        dev.dimension.flare.data.database.cache.model.DbStatus::class,
        dev.dimension.flare.data.database.cache.model.DbUser::class,
        dev.dimension.flare.data.database.cache.model.DbPagingTimeline::class,
    ],
    version = 1,
)
@TypeConverters(
    dev.dimension.flare.data.database.adapter.MicroBlogKeyConverter::class,
    dev.dimension.flare.data.database.adapter.PlatformTypeConverter::class,
    dev.dimension.flare.data.database.cache.model.EmojiContentConverter::class,
    dev.dimension.flare.data.database.cache.model.StatusConverter::class,
    dev.dimension.flare.data.database.cache.model.UserContentConverters::class,
)
@ConstructedBy(CacheDatabaseConstructor::class)
abstract class CacheDatabase : RoomDatabase() {
    abstract fun emojiDao(): dev.dimension.flare.data.database.cache.dao.EmojiDao

    abstract fun statusReferenceDao(): dev.dimension.flare.data.database.cache.dao.StatusReferenceDao

    abstract fun statusDao(): dev.dimension.flare.data.database.cache.dao.StatusDao

    abstract fun userDao(): dev.dimension.flare.data.database.cache.dao.UserDao

    abstract fun pagingTimelineDao(): dev.dimension.flare.data.database.cache.dao.PagingTimelineDao
}

// The Room compiler generates the `actual` implementations.
@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object CacheDatabaseConstructor : RoomDatabaseConstructor<CacheDatabase> {
    override fun initialize(): CacheDatabase
}
