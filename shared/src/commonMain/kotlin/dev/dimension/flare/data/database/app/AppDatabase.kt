package dev.dimension.flare.data.database.app

import androidx.room.AutoMigration
import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import dev.dimension.flare.data.database.app.dao.AccountDao
import dev.dimension.flare.data.database.app.dao.ApplicationDao
import dev.dimension.flare.data.database.app.dao.GuestDataDao
import dev.dimension.flare.data.database.app.dao.KeywordFilterDao
import dev.dimension.flare.data.database.app.dao.SearchHistoryDao

@Database(
    entities = [
        dev.dimension.flare.data.database.app.model.DbAccount::class,
        dev.dimension.flare.data.database.app.model.DbApplication::class,
        dev.dimension.flare.data.database.app.model.DbKeywordFilter::class,
        dev.dimension.flare.data.database.app.model.DbSearchHistory::class,
        dev.dimension.flare.data.database.app.model.DbGuestData::class,
    ],
    version = 4,
    autoMigrations = [
        AutoMigration(
            from = 3,
            to = 4,
        ),
    ],
)
@TypeConverters(
    dev.dimension.flare.data.database.adapter.MicroBlogKeyConverter::class,
    dev.dimension.flare.data.database.adapter.PlatformTypeConverter::class,
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao

    abstract fun applicationDao(): ApplicationDao

    abstract fun keywordFilterDao(): KeywordFilterDao

    abstract fun searchHistoryDao(): SearchHistoryDao

    abstract fun guestDataDao(): GuestDataDao
}

// The Room compiler generates the `actual` implementations.
@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}
