package dev.dimension.flare.data.database.app

import androidx.room3.AutoMigration
import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.room3.TypeConverters
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
    version = 8,
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
        AutoMigration(
            from = 6,
            to = 7,
        ),
        AutoMigration(
            from = 7,
            to = 8,
        ),
    ],
    exportSchema = true,
)
@TypeConverters(
    dev.dimension.flare.data.database.adapter.MicroBlogKeyConverter::class,
    dev.dimension.flare.data.database.adapter.PlatformTypeConverter::class,
    dev.dimension.flare.data.database.app.model.DraftConverters::class,
    dev.dimension.flare.data.database.adapter.SubscriptionTypeConverter::class,
)
@ConstructedBy(AppDatabaseConstructor::class)
public abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao

    abstract fun applicationDao(): ApplicationDao

    abstract fun draftDao(): DraftDao

    abstract fun keywordFilterDao(): KeywordFilterDao

    abstract fun searchHistoryDao(): SearchHistoryDao

    abstract fun rssSourceDao(): RssSourceDao
}

// The Room compiler generates the `actual` implementations.
@Suppress("NO_ACTUAL_FOR_EXPECT")
public expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}
