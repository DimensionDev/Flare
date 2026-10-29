package dev.dimension.flare.data.database

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.async.executeSQL
import dev.dimension.flare.common.PlatformDispatchers
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.TimelineRevisionCallback
import org.koin.core.annotation.Single

@Single
internal fun provideAppDatabase(driverFactory: DriverFactory): AppDatabase =
    driverFactory
        .createBuilder<AppDatabase>("app.db")
        .addMigrations(
            AppDatabase.MIGRATION_8_9,
            AppDatabase.MIGRATION_9_10,
            AppDatabase.MIGRATION_10_11,
            AppDatabase.MIGRATION_11_12,
            PLUGIN_DEVELOPMENT_DOWNGRADE,
        ).setDriver(createDatabaseDriver())
        .setQueryCoroutineContext(PlatformDispatchers.IO)
        .build()

internal const val CACHE_DATABASE_NAME = "cache.db"

@Single
internal fun provideCacheDatabase(driverFactory: DriverFactory): CacheDatabase =
    driverFactory
        .createBuilder<CacheDatabase>(CACHE_DATABASE_NAME, isCache = true)
        .fallbackToDestructiveMigration(dropAllTables = true)
        .addCallback(TimelineRevisionCallback)
        .setDriver(createDatabaseDriver())
        .setQueryCoroutineContext(PlatformDispatchers.IO)
        .build()

// v13 was development-only; remove its display snapshots without deleting accounts or other app data.
internal val PLUGIN_DEVELOPMENT_DOWNGRADE =
    object : Migration(13, 12) {
        override suspend fun migrate(connection: SQLiteConnection) {
            listOf("platform_display_name", "platform_icon_name", "platform_display_name_text_json", "platform_icon_url").forEach {
                connection.executeSQL("ALTER TABLE DbAccount DROP COLUMN $it")
            }
        }
    }
