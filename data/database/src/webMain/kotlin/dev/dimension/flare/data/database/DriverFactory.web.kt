package dev.dimension.flare.data.database

import androidx.room3.Room
import org.koin.core.annotation.Singleton

@Singleton
public actual class DriverFactory {
    internal actual inline fun <reified T : androidx.room3.RoomDatabase> createBuilder(
        name: String,
        isCache: Boolean,
    ): androidx.room3.RoomDatabase.Builder<T> =
        Room.databaseBuilder<T>(
            name = name,
        )
}
