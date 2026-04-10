package dev.dimension.flare

import androidx.room3.Room
import androidx.room3.RoomDatabase
import kotlin.reflect.KClass

internal expect fun <T : RoomDatabase> Room.memoryDatabaseBuilder(databaseClass: KClass<T>): RoomDatabase.Builder<T>

internal inline fun <reified T : RoomDatabase> Room.memoryDatabaseBuilder(): RoomDatabase.Builder<T> = memoryDatabaseBuilder(T::class)

expect open class RobolectricTest()
