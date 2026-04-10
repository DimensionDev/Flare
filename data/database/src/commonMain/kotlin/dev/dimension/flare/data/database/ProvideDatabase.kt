package dev.dimension.flare.data.database

import androidx.room3.RoomDatabase
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.database.cache.CacheDatabase

internal expect fun <T : RoomDatabase> RoomDatabase.Builder<T>.platformOptions(): RoomDatabase.Builder<T>

public fun provideAppDatabase(driverFactory: DriverFactory): AppDatabase =
    driverFactory
        .createBuilder<AppDatabase>("app.db")
        .platformOptions()
        .build()

public fun provideCacheDatabase(driverFactory: DriverFactory): CacheDatabase =
    driverFactory
        .createBuilder<CacheDatabase>("cache.db", isCache = true)
        .fallbackToDestructiveMigration(dropAllTables = true)
        .platformOptions()
        .build()
