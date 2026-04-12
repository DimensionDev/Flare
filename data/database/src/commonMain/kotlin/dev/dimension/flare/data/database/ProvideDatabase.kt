package dev.dimension.flare.data.database

import androidx.sqlite.SQLiteDriver
import dev.dimension.flare.common.PlatformIO
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.database.cache.CacheDatabase
import kotlinx.coroutines.Dispatchers
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module
import org.koin.core.annotation.Singleton

internal expect fun createSQLiteDriver(): SQLiteDriver

@Singleton
public fun provideAppDatabase(driverFactory: DriverFactory): AppDatabase =
    driverFactory
        .createBuilder<AppDatabase>("app.db")
        .setDriver(createSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.PlatformIO)
        .build()

@Singleton
public fun provideCacheDatabase(driverFactory: DriverFactory): CacheDatabase =
    driverFactory
        .createBuilder<CacheDatabase>("cache.db", isCache = true)
        .fallbackToDestructiveMigration(dropAllTables = true)
        .setDriver(createSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.PlatformIO)
        .build()

@Module
@ComponentScan
@Configuration
public class DatabaseModule
