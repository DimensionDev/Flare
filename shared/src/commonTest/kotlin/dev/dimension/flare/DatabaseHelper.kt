package dev.dimension.flare

import androidx.room.Room
import androidx.room.RoomDatabase

internal expect inline fun <reified T : RoomDatabase> Room.memoryDatabaseBuilder(): RoomDatabase.Builder<T>

expect open class RobolectricTest()
