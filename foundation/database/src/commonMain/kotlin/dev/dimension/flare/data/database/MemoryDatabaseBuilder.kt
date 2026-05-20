package dev.dimension.flare.data.database

import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.SQLiteDriver
import kotlin.reflect.KClass

public inline fun <reified T : RoomDatabase> Room.memoryDatabaseBuilder(
    databaseDriver: SQLiteDriver = createDatabaseDriver(),
): RoomDatabase.Builder<T> =
    platformMemoryDatabaseBuilder(T::class)
        .setDriver(databaseDriver)

@PublishedApi
internal expect fun <T : RoomDatabase> Room.platformMemoryDatabaseBuilder(databaseClass: KClass<T>): RoomDatabase.Builder<T>
