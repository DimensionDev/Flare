package dev.dimension.flare.data.database

import androidx.room3.Room
import androidx.room3.RoomDatabase

public actual class DriverFactory {
    public actual inline fun <reified T : RoomDatabase> createBuilder(
        name: String,
        isCache: Boolean,
    ): RoomDatabase.Builder<T> =
        Room.databaseBuilder<T>(
            name = name,
        )

    public actual fun deleteDatabase(
        name: String,
        isCache: Boolean,
    ) {
    }
}
