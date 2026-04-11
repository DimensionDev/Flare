package dev.dimension.flare.data.database.cache

import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.room3.TypeConverters
import androidx.room3.immediateTransaction
import androidx.room3.useWriterConnection

public const val CACHE_DATABASE_VERSION: Int = 37

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
        dev.dimension.flare.data.database.cache.model.DbEmojiHistory::class,
        dev.dimension.flare.data.database.cache.model.DbPagingKey::class,
        dev.dimension.flare.data.database.cache.model.DbList::class,
        dev.dimension.flare.data.database.cache.model.DbListPaging::class,
        dev.dimension.flare.data.database.cache.model.DbListMember::class,
        dev.dimension.flare.data.database.cache.model.DbUserRelation::class,
        dev.dimension.flare.data.database.cache.model.DbTranslation::class,
    ],
    version = CACHE_DATABASE_VERSION,
    exportSchema = false,
)
@TypeConverters(
    dev.dimension.flare.data.database.adapter.MicroBlogKeyConverter::class,
    dev.dimension.flare.data.database.adapter.PlatformTypeConverter::class,
    dev.dimension.flare.data.database.adapter.AccountTypeConverter::class,
    dev.dimension.flare.data.database.cache.model.EmojiContentConverter::class,
    dev.dimension.flare.data.database.cache.model.StatusConverter::class,
    dev.dimension.flare.data.database.cache.model.MessageContentConverters::class,
    dev.dimension.flare.data.database.cache.model.ListContentConverters::class,
    dev.dimension.flare.data.database.cache.model.TranslationConverters::class,
)
@ConstructedBy(CacheDatabaseConstructor::class)
public abstract class CacheDatabase : RoomDatabase() {
    public abstract fun emojiDao(): dev.dimension.flare.data.database.cache.dao.EmojiDao

    public abstract fun statusReferenceDao(): dev.dimension.flare.data.database.cache.dao.StatusReferenceDao

    public abstract fun statusDao(): dev.dimension.flare.data.database.cache.dao.StatusDao

    public abstract fun userDao(): dev.dimension.flare.data.database.cache.dao.UserDao

    public abstract fun pagingTimelineDao(): dev.dimension.flare.data.database.cache.dao.PagingTimelineDao

    public abstract fun messageDao(): dev.dimension.flare.data.database.cache.dao.MessageDao

    public abstract fun listDao(): dev.dimension.flare.data.database.cache.dao.ListDao

    public abstract fun translationDao(): dev.dimension.flare.data.database.cache.dao.TranslationDao
}

// The Room compiler generates the `actual` implementations.
@Suppress("NO_ACTUAL_FOR_EXPECT")
public expect object CacheDatabaseConstructor : RoomDatabaseConstructor<CacheDatabase> {
    public override fun initialize(): CacheDatabase
}

public suspend fun <R> RoomDatabase.connect(block: suspend () -> R): R =
    useWriterConnection {
        it.immediateTransaction {
            block.invoke()
        }
    }
