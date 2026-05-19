package dev.dimension.flare.data.database.app

import androidx.room3.AutoMigration
import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.room3.TypeConverters
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.async.executeSQL
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
    version = 10,
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
    dev.dimension.flare.data.database.adapter.RssDisplayModeConverter::class,
)
@ConstructedBy(AppDatabaseConstructor::class)
public abstract class AppDatabase : RoomDatabase() {
    public abstract fun accountDao(): AccountDao

    public abstract fun applicationDao(): ApplicationDao

    public abstract fun draftDao(): DraftDao

    public abstract fun keywordFilterDao(): KeywordFilterDao

    public abstract fun searchHistoryDao(): SearchHistoryDao

    public abstract fun rssSourceDao(): RssSourceDao

    public companion object {
        public val MIGRATION_8_9: Migration =
            object : Migration(8, 9) {
                override suspend fun migrate(connection: SQLiteConnection) {
                    connection.executeSQL(
                        "ALTER TABLE DbRssSources ADD COLUMN displayMode TEXT NOT NULL DEFAULT 'FULL_CONTENT'",
                    )
                    connection.executeSQL(
                        "UPDATE DbRssSources SET displayMode = 'OPEN_IN_BROWSER' WHERE openInBrowser = 1",
                    )
                    connection.executeSQL(
                        "ALTER TABLE DbRssSources DROP COLUMN openInBrowser",
                    )
                }
            }
        public val MIGRATION_9_10: Migration =
            object : Migration(9, 10) {
                override suspend fun migrate(connection: SQLiteConnection) {
                    connection.executeSQL(
                        "ALTER TABLE DbKeywordFilter ADD COLUMN is_regex INTEGER NOT NULL DEFAULT 0",
                    )
                }
            }
    }
}

// The Room compiler generates the `actual` implementations.
@Suppress("NO_ACTUAL_FOR_EXPECT")
public expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}
