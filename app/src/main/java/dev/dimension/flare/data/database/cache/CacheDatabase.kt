package dev.dimension.flare.data.database.cache

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.moriatsushi.koject.Provides
import com.moriatsushi.koject.Singleton
import dev.dimension.flare.data.database.Converters
import dev.dimension.flare.data.database.cache.dao.EmojiDao
import dev.dimension.flare.data.database.cache.dao.PagingTimelineDao
import dev.dimension.flare.data.database.cache.dao.StatusDao
import dev.dimension.flare.data.database.cache.dao.StatusReferenceDao
import dev.dimension.flare.data.database.cache.dao.UserDao
import dev.dimension.flare.data.database.cache.model.DbEmoji
import dev.dimension.flare.data.database.cache.model.DbPagingTimeline
import dev.dimension.flare.data.database.cache.model.DbStatus
import dev.dimension.flare.data.database.cache.model.DbStatusReference
import dev.dimension.flare.data.database.cache.model.DbUser

@Database(
    entities = [
        DbStatus::class,
        DbUser::class,
        DbStatusReference::class,
        DbPagingTimeline::class,
        DbEmoji::class
    ],
    version = 4
)
@TypeConverters(Converters::class)
abstract class CacheDatabase : RoomDatabase() {
    abstract fun statusDao(): StatusDao
    abstract fun userDao(): UserDao
    abstract fun statusReferenceDao(): StatusReferenceDao
    abstract fun pagingTimelineDao(): PagingTimelineDao
    abstract fun emojiDao(): EmojiDao
}

@Singleton
@Provides
fun provideCacheDatabase(
    applicationContext: Context
): CacheDatabase {
    return Room
        .databaseBuilder(
            applicationContext,
            CacheDatabase::class.java,
            "cache_database"
        )
        .fallbackToDestructiveMigration()
        .build()
}
