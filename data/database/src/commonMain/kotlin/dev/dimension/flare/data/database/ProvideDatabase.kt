package dev.dimension.flare.data.database

import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.database.cache.CacheDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

public fun provideAppDatabase(driverFactory: DriverFactory): AppDatabase =
    driverFactory
        .createBuilder<AppDatabase>("app.db")
        .addMigrations(AppDatabase.MIGRATION_8_9, AppDatabase.MIGRATION_9_10)
        .setDriver(createDatabaseDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

public const val CACHE_DATABASE_NAME: String = "cache.db"

public fun provideCacheDatabase(driverFactory: DriverFactory): CacheDatabase =
    driverFactory
        .createBuilder<CacheDatabase>(CACHE_DATABASE_NAME, isCache = true)
        .fallbackToDestructiveMigration(dropAllTables = true)
        .setDriver(createDatabaseDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
