package dev.dimension.flare.data.database

import androidx.room3.Room

public actual class DriverFactory {
    actual inline fun <reified T : androidx.room3.RoomDatabase> createBuilder(
        name: String,
        isCache: Boolean
    ): androidx.room3.RoomDatabase.Builder<T> {
        return Room.databaseBuilder<T>(
            name = name,
        )
    }
}