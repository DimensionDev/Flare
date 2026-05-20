package dev.dimension.flare.data.database

import androidx.room3.Room
import androidx.room3.RoomDatabase
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.database.cache.CacheDatabase
import kotlin.reflect.KClass

@PublishedApi
@Suppress("UNCHECKED_CAST")
internal actual fun <T : RoomDatabase> Room.platformMemoryDatabaseBuilder(databaseClass: KClass<T>): RoomDatabase.Builder<T> =
    when (databaseClass) {
        AppDatabase::class -> Room.inMemoryDatabaseBuilder<AppDatabase>()
        CacheDatabase::class -> Room.inMemoryDatabaseBuilder<CacheDatabase>()
        else -> error("Unsupported test database: ${databaseClass.simpleName}")
    } as RoomDatabase.Builder<T>
