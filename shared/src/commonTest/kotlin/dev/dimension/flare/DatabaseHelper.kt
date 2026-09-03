package dev.dimension.flare

import androidx.room3.Room
import androidx.room3.RoomDatabase
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.TimelineRevisionCallback
import kotlin.reflect.KClass

internal expect fun <T : RoomDatabase> Room.memoryDatabaseBuilder(databaseClass: KClass<T>): RoomDatabase.Builder<T>

internal inline fun <reified T : RoomDatabase> Room.memoryDatabaseBuilder(): RoomDatabase.Builder<T> =
    memoryDatabaseBuilder(T::class).apply {
        if (T::class == CacheDatabase::class) {
            addCallback(TimelineRevisionCallback)
        }
    }

expect open class RobolectricTest()
