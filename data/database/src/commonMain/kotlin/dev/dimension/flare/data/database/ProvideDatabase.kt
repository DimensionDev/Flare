package dev.dimension.flare.data.database

import androidx.room3.RoomDatabase
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.database.cache.CacheDatabase
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module
import org.koin.core.annotation.Singleton

internal expect fun <T : RoomDatabase> RoomDatabase.Builder<T>.platformOptions(): RoomDatabase.Builder<T>

@Singleton
public fun provideAppDatabase(driverFactory: DriverFactory): AppDatabase =
    driverFactory
        .createBuilder<AppDatabase>("app.db")
        .platformOptions()
        .build()

@Singleton
public fun provideCacheDatabase(driverFactory: DriverFactory): CacheDatabase =
    driverFactory
        .createBuilder<CacheDatabase>("cache.db", isCache = true)
        .fallbackToDestructiveMigration(dropAllTables = true)
        .platformOptions()
        .build()

@Module
@ComponentScan
@Configuration
public class DatabaseModule
