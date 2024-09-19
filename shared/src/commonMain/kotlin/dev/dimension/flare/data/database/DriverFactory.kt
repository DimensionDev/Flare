package dev.dimension.flare.data.database

import androidx.room.RoomDatabase

internal expect class DriverFactory {
    inline fun <reified T : RoomDatabase> createBuilder(name: String): RoomDatabase.Builder<T>

    fun deleteDatabase(name: String)
}
