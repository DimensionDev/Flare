package dev.dimension.flare.data.database.app

import androidx.room.AutoMigration
import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import dev.dimension.flare.data.database.app.dao.AccountDao
import dev.dimension.flare.data.database.app.dao.ApplicationDao
import dev.dimension.flare.data.database.app.dao.DraftDao
import dev.dimension.flare.data.database.app.dao.KeywordFilterDao
import dev.dimension.flare.data.database.app.dao.RssSourceDao
import dev.dimension.flare.data.database.app.dao.SearchHistoryDao

@Database(
    entities = [
        dev.dimension.flare.data.database.app.model.DbAccount::class,
        dev.dimension.flare.data.database.app.model.DbApplication::class,
        dev.dimension.flare.data.database.app.model.DbDraftGroup::class,
        dev.dimension.flare.data.database.app.model.DbDraftTarget::class,
        dev.dimension.flare.data.database.app.model.DbDraftMedia::class,
        dev.dimension.flare.data.database.app.model.DbKeywordFilter::class,
        dev.dimension.flare.data.database.app.model.DbSearchHistory::class,
        dev.dimension.flare.data.database.app.model.DbRssSources::class,
    ],
    version = 6,
    autoMigrations = [
        AutoMigration(
            from = 3,
            to = 4,
        ),
        AutoMigration(
            from = 4,
            to = 5,
        ),
        AutoMigration(
            from = 5,
            to = 6,
        ),
    ],
    exportSchema = true,
)
@TypeConverters(
    dev.dimension.flare.data.database.adapter.MicroBlogKeyConverter::class,
    dev.dimension.flare.data.database.adapter.PlatformTypeConverter::class,
    dev.dimension.flare.data.database.app.model.DraftConverters::class,
)
@ConstructedBy(AppDatabaseConstructor::class)
internal abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao

    abstract fun applicationDao(): ApplicationDao

    abstract fun draftDao(): DraftDao

    abstract fun keywordFilterDao(): KeywordFilterDao

    abstract fun searchHistoryDao(): SearchHistoryDao

    abstract fun rssSourceDao(): RssSourceDao
}

// The Room compiler generates the `actual` implementations.
@Suppress("NO_ACTUAL_FOR_EXPECT")
internal expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}
