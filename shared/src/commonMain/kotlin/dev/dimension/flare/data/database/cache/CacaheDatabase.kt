package dev.dimension.flare.data.database.cache

import androidx.room3.ColumnTypeConverters
import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.room3.withWriteTransaction
import androidx.sqlite.async.executeSQL

internal const val CACHE_DATABASE_VERSION = 51

@Database(
    entities = [
        dev.dimension.flare.data.database.cache.model.DbEmoji::class,
        dev.dimension.flare.data.database.cache.model.DbStatusReference::class,
        dev.dimension.flare.data.database.cache.model.DbStatus::class,
        dev.dimension.flare.data.database.cache.model.DbUser::class,
        dev.dimension.flare.data.database.cache.model.DbPagingTimeline::class,
        dev.dimension.flare.data.database.cache.model.DbTimelineItemPresentationReference::class,
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
        dev.dimension.flare.data.database.cache.model.DbTimelineHistory::class,
        dev.dimension.flare.data.database.cache.model.DbTimelineBatch::class,
        dev.dimension.flare.data.database.cache.model.DbTimelineEntry::class,
        dev.dimension.flare.data.database.cache.model.DbTimelineBookmark::class,
        dev.dimension.flare.data.database.cache.model.DbTimelineBinding::class,
    ],
    version = CACHE_DATABASE_VERSION,
    exportSchema = false,
)
@ColumnTypeConverters(
    dev.dimension.flare.data.database.adapter.MicroBlogKeyConverter::class,
    dev.dimension.flare.data.database.adapter.AccountTypeConverter::class,
    dev.dimension.flare.data.database.cache.model.EmojiContentConverter::class,
    dev.dimension.flare.data.database.cache.model.StatusConverter::class,
    dev.dimension.flare.data.database.cache.model.DbStatusContentConverter::class,
    dev.dimension.flare.data.database.cache.model.ListContentConverters::class,
    dev.dimension.flare.data.database.cache.model.TranslationConverters::class,
)
@ConstructedBy(CacheDatabaseConstructor::class)
internal abstract class CacheDatabase : RoomDatabase() {
    companion object {
        val MIGRATION_50_51 =
            object : androidx.room3.migration.Migration(50, 51) {
                override suspend fun migrate(connection: androidx.sqlite.SQLiteConnection) {
                    connection.executeSQL("ALTER TABLE `DbTimelineBatch` ADD COLUMN `sourceHeads` TEXT DEFAULT NULL")
                }
            }

        val MIGRATION_49_50 =
            object : androidx.room3.migration.Migration(49, 50) {
                override suspend fun migrate(connection: androidx.sqlite.SQLiteConnection) {
                    connection.executeSQL("ALTER TABLE `DbTimelineBatch` ADD COLUMN `pageKeys` TEXT DEFAULT NULL")
                    connection.executeSQL("ALTER TABLE `DbTimelineBatch` ADD COLUMN `gapAfterItemKey` TEXT DEFAULT NULL")
                    connection.executeSQL("ALTER TABLE `DbTimelineEntry` ADD COLUMN `createdAt` INTEGER DEFAULT NULL")
                    connection.executeSQL("ALTER TABLE `DbTimelineBookmark` ADD COLUMN `seenItemKey` TEXT DEFAULT NULL")
                    connection.executeSQL("ALTER TABLE `DbTimelineBookmark` ADD COLUMN `seenOrdinal` INTEGER DEFAULT NULL")
                    connection.executeSQL("ALTER TABLE `DbTimelineBookmark` ADD COLUMN `seenPosition` INTEGER DEFAULT NULL")
                    connection.executeSQL(
                        "CREATE TABLE IF NOT EXISTS `DbTimelineBinding` (`tabId` TEXT NOT NULL, `historyKey` TEXT NOT NULL, `generation` TEXT NOT NULL, PRIMARY KEY(`tabId`))",
                    )
                    connection.executeSQL(
                        "CREATE INDEX IF NOT EXISTS `index_DbTimelineBinding_historyKey` ON `DbTimelineBinding` (`historyKey`)",
                    )
                    connection.executeSQL("INSERT INTO `DbTimelineBinding` SELECT tabId, historyKey, tabId FROM `DbTimelineBookmark`")
                }
            }

        val MIGRATION_48_49 =
            object : androidx.room3.migration.Migration(48, 49) {
                override suspend fun migrate(connection: androidx.sqlite.SQLiteConnection) {
                    connection.executeSQL(
                        "CREATE TABLE IF NOT EXISTS `DbTimelineHistory` (`key` TEXT NOT NULL, `generation` TEXT NOT NULL, PRIMARY KEY(`key`))",
                    )
                    connection.executeSQL(
                        "CREATE TABLE IF NOT EXISTS `DbTimelineBatch` (`id` TEXT NOT NULL, `historyKey` TEXT NOT NULL, " +
                            "`ordinal` INTEGER NOT NULL, `nextKey` TEXT, `manual` INTEGER NOT NULL, `lowerItemKey` TEXT, PRIMARY KEY(`id`))",
                    )
                    connection.executeSQL(
                        "CREATE INDEX IF NOT EXISTS `index_DbTimelineBatch_historyKey_ordinal` ON `DbTimelineBatch` (`historyKey`, `ordinal`)",
                    )
                    connection.executeSQL(
                        "CREATE TABLE IF NOT EXISTS `DbTimelineEntry` (`historyKey` TEXT NOT NULL, `itemKey` TEXT NOT NULL, " +
                            "`timelineId` TEXT NOT NULL, `batchId` TEXT NOT NULL, `position` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`historyKey`, `itemKey`))",
                    )
                    connection.executeSQL(
                        "CREATE INDEX IF NOT EXISTS `index_DbTimelineEntry_batchId_position` ON `DbTimelineEntry` (`batchId`, `position`)",
                    )
                    connection.executeSQL(
                        "CREATE INDEX IF NOT EXISTS `index_DbTimelineEntry_timelineId` ON `DbTimelineEntry` (`timelineId`)",
                    )
                    connection.executeSQL(
                        "CREATE TABLE IF NOT EXISTS `DbTimelineBookmark` (`tabId` TEXT NOT NULL, `historyKey` TEXT NOT NULL, " +
                            "`itemKey` TEXT NOT NULL, `batchOrdinal` INTEGER NOT NULL, `position` INTEGER NOT NULL, " +
                            "`offset` REAL NOT NULL, PRIMARY KEY(`tabId`))",
                    )
                }
            }
    }

    abstract fun emojiDao(): dev.dimension.flare.data.database.cache.dao.EmojiDao

    abstract fun statusReferenceDao(): dev.dimension.flare.data.database.cache.dao.StatusReferenceDao

    abstract fun statusDao(): dev.dimension.flare.data.database.cache.dao.StatusDao

    abstract fun userDao(): dev.dimension.flare.data.database.cache.dao.UserDao

    abstract fun pagingTimelineDao(): dev.dimension.flare.data.database.cache.dao.PagingTimelineDao

    abstract fun messageDao(): dev.dimension.flare.data.database.cache.dao.MessageDao

    abstract fun listDao(): dev.dimension.flare.data.database.cache.dao.ListDao

    abstract fun translationDao(): dev.dimension.flare.data.database.cache.dao.TranslationDao

    abstract fun timelineHistoryDao(): dev.dimension.flare.data.database.cache.dao.TimelineHistoryDao
}

// The Room compiler generates the `actual` implementations.
@Suppress("NO_ACTUAL_FOR_EXPECT")
internal expect object CacheDatabaseConstructor : RoomDatabaseConstructor<CacheDatabase> {
    override fun initialize(): CacheDatabase
}

internal suspend fun <R> RoomDatabase.connect(block: suspend () -> R): R = withWriteTransaction { block() }
