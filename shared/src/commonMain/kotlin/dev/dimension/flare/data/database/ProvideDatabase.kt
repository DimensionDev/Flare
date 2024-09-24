package dev.dimension.flare.data.database

import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.database.cache.CacheDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

internal fun provideAppDatabase(driverFactory: DriverFactory): AppDatabase =
    driverFactory
        .createBuilder<AppDatabase>("app.db")
        .setDriver(driverFactory.createSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

internal fun provideCacheDatabase(driverFactory: DriverFactory): CacheDatabase =
    driverFactory
        .createBuilder<CacheDatabase>("cache.db", isCache = true)
        .fallbackToDestructiveMigration(dropAllTables = true)
        .setDriver(driverFactory.createSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
