package dev.dimension.flare

import androidx.room.Room
import androidx.room.RoomDatabase

internal actual inline fun <reified T : RoomDatabase> Room.memoryDatabaseBuilder(): RoomDatabase.Builder<T> = Room.inMemoryDatabaseBuilder()

actual open class RobolectricTest actual constructor()
