package dev.dimension.flare.data.database.app

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class AppDatabaseMigration11To12Test {
    @Test
    fun migrationPreservesEveryExistingPlatformAccountField() =
        runTest {
            val databasePath = Files.createTempFile("flare-app-v11", ".db")
            val path = databasePath.toAbsolutePath().toString()
            val platformIds = listOf("Mastodon", "Misskey", "Bluesky", "Pixiv", "xQt", "VVo", "Nostr", "Fanbox")
            try {
                createVersion11Fixture(path, platformIds)

                val database =
                    Room
                        .databaseBuilder<AppDatabase>(name = path)
                        .addMigrations(AppDatabase.MIGRATION_11_12)
                        .setDriver(BundledSQLiteDriver())
                        .setQueryCoroutineContext(Dispatchers.Unconfined)
                        .build()
                try {
                    val accounts = database.accountDao().sortedAccounts().first()
                    assertEquals(platformIds, accounts.map { it.platformId })
                    accounts.forEachIndexed { index, account ->
                        val platformId = platformIds[index]
                        assertEquals(MicroBlogKey("user-$index", "fixture.example"), account.account_key)
                        assertEquals("{\"token\":\"$platformId\"}", account.credential_json)
                        assertEquals(1_000L + index, account.last_active)
                        assertEquals(index.toLong(), account.sort_id)
                    }
                } finally {
                    database.close()
                }

                BundledSQLiteDriver().open(path).use { connection ->
                    connection.prepare("PRAGMA user_version").use { statement ->
                        assertEquals(true, statement.step())
                        assertEquals(12L, statement.getLong(0))
                    }
                    connection.prepare("SELECT identity_hash FROM room_master_table WHERE id = 42").use { statement ->
                        assertEquals(true, statement.step())
                        assertEquals(APP_DATABASE_IDENTITY_HASH, statement.getText(0))
                    }
                }
            } finally {
                Files.deleteIfExists(databasePath)
            }
        }

    private fun createVersion11Fixture(
        path: String,
        platformIds: List<String>,
    ) {
        BundledSQLiteDriver().open(path).use { connection ->
            V11_SCHEMA.forEach(connection::execSQL)
            connection.execSQL("PRAGMA user_version = 11")
            connection.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            connection.execSQL("INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES(42, '$APP_DATABASE_IDENTITY_HASH')")
            connection
                .prepare(
                    "INSERT INTO DbAccount(account_key, credential_json, platform_type, last_active, sort_id) VALUES (?, ?, ?, ?, ?)",
                ).use { statement ->
                    platformIds.forEachIndexed { index, platformId ->
                        statement.bindText(1, "user-$index@fixture.example")
                        statement.bindText(2, "{\"token\":\"$platformId\"}")
                        statement.bindText(3, platformId)
                        statement.bindLong(4, 1_000L + index)
                        statement.bindLong(5, index.toLong())
                        statement.step()
                        statement.reset()
                        statement.clearBindings()
                    }
                }
        }
    }

    private companion object {
        const val APP_DATABASE_IDENTITY_HASH = "30af886dd6bd572e9c4c3d06afdb8a68"

        val V11_SCHEMA =
            listOf(
                "CREATE TABLE IF NOT EXISTS DbAccount (account_key TEXT NOT NULL, credential_json TEXT NOT NULL, platform_type TEXT NOT NULL, last_active INTEGER NOT NULL, sort_id INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(account_key))",
                "CREATE TABLE IF NOT EXISTS DbDraftGroup (group_id TEXT NOT NULL, content TEXT NOT NULL, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, PRIMARY KEY(group_id))",
                "CREATE INDEX IF NOT EXISTS index_DbDraftGroup_updated_at ON DbDraftGroup(updated_at)",
                "CREATE TABLE IF NOT EXISTS DbDraftTarget (group_id TEXT NOT NULL, account_key TEXT NOT NULL, status TEXT NOT NULL, error_message TEXT, attempt_count INTEGER NOT NULL, last_attempt_at INTEGER, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, target_id TEXT NOT NULL, PRIMARY KEY(target_id), FOREIGN KEY(group_id) REFERENCES DbDraftGroup(group_id) ON UPDATE NO ACTION ON DELETE CASCADE)",
                "CREATE INDEX IF NOT EXISTS index_DbDraftTarget_group_id ON DbDraftTarget(group_id)",
                "CREATE INDEX IF NOT EXISTS index_DbDraftTarget_account_key ON DbDraftTarget(account_key)",
                "CREATE INDEX IF NOT EXISTS index_DbDraftTarget_status ON DbDraftTarget(status)",
                "CREATE UNIQUE INDEX IF NOT EXISTS index_DbDraftTarget_group_id_account_key ON DbDraftTarget(group_id, account_key)",
                "CREATE TABLE IF NOT EXISTS DbDraftMedia (group_id TEXT NOT NULL, cache_path TEXT NOT NULL, file_name TEXT, media_type TEXT NOT NULL, alt_text TEXT, sort_order INTEGER NOT NULL, created_at INTEGER NOT NULL, media_id TEXT NOT NULL, PRIMARY KEY(media_id), FOREIGN KEY(group_id) REFERENCES DbDraftGroup(group_id) ON UPDATE NO ACTION ON DELETE CASCADE)",
                "CREATE INDEX IF NOT EXISTS index_DbDraftMedia_group_id ON DbDraftMedia(group_id)",
                "CREATE INDEX IF NOT EXISTS index_DbDraftMedia_group_id_sort_order ON DbDraftMedia(group_id, sort_order)",
                "CREATE TABLE IF NOT EXISTS DbKeywordFilter (keyword TEXT NOT NULL, for_timeline INTEGER NOT NULL, for_notification INTEGER NOT NULL, for_search INTEGER NOT NULL, expired_at INTEGER NOT NULL, is_regex INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(keyword))",
                "CREATE TABLE IF NOT EXISTS DbSearchHistory (search TEXT NOT NULL, created_at INTEGER NOT NULL, PRIMARY KEY(search))",
                "CREATE TABLE IF NOT EXISTS DbRssSources (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, url TEXT NOT NULL, title TEXT, icon TEXT, displayMode TEXT NOT NULL DEFAULT 'FULL_CONTENT', lastUpdate INTEGER NOT NULL, type TEXT NOT NULL DEFAULT 'RSS')",
            )
    }
}
