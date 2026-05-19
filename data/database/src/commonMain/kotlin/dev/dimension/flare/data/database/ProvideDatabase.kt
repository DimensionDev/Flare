package dev.dimension.flare.data.database

import androidx.sqlite.SQLiteDriver
import dev.dimension.flare.common.PlatformDispatchers
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.database.cache.CacheDatabase

public fun provideAppDatabase(driverFactory: DriverFactory): AppDatabase =
    provideAppDatabase(
        driverFactory = driverFactory,
        databaseDriver = createDatabaseDriver(),
    )

public fun provideAppDatabase(
    driverFactory: DriverFactory,
    databaseDriver: SQLiteDriver,
): AppDatabase =
    driverFactory
        .createBuilder<AppDatabase>("app.db")
        .addMigrations(AppDatabase.MIGRATION_8_9, AppDatabase.MIGRATION_9_10)
        .setDriver(databaseDriver)
        .setQueryCoroutineContext(PlatformDispatchers.IO)
        .build()

public const val CACHE_DATABASE_NAME: String = "cache.db"

public fun provideCacheDatabase(driverFactory: DriverFactory): CacheDatabase =
    provideCacheDatabase(
        driverFactory = driverFactory,
        databaseDriver = createDatabaseDriver(),
    )

public fun provideCacheDatabase(
    driverFactory: DriverFactory,
    databaseDriver: SQLiteDriver,
): CacheDatabase =
    driverFactory
        .createBuilder<CacheDatabase>(CACHE_DATABASE_NAME, isCache = true)
        .fallbackToDestructiveMigration(dropAllTables = true)
        .setDriver(databaseDriver)
        .setQueryCoroutineContext(PlatformDispatchers.IO)
        .build()
