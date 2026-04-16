package dev.dimension.flare.data.database

import androidx.room3.RoomDatabase

internal expect class DriverFactory {
    inline fun <reified T : RoomDatabase> createBuilder(
        name: String,
        isCache: Boolean = false,
    ): RoomDatabase.Builder<T>
}
