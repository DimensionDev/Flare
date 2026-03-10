package dev.dimension.flare

import androidx.room.Room
import androidx.room.RoomDatabase
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.database.app.AppDatabaseConstructor
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.CacheDatabaseConstructor
import kotlin.reflect.KClass

@Suppress("UNCHECKED_CAST")
internal actual fun <T : RoomDatabase> Room.memoryDatabaseBuilder(databaseClass: KClass<T>): RoomDatabase.Builder<T> =
    when (databaseClass) {
        AppDatabase::class -> Room.inMemoryDatabaseBuilder(factory = AppDatabaseConstructor::initialize)
        CacheDatabase::class -> Room.inMemoryDatabaseBuilder(factory = CacheDatabaseConstructor::initialize)
        else -> error("Unsupported test database: ${databaseClass.qualifiedName}")
    } as RoomDatabase.Builder<T>

actual open class RobolectricTest actual constructor()
