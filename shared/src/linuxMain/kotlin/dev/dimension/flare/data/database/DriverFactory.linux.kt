package dev.dimension.flare.data.database

import androidx.room.Room
import androidx.room.RoomDatabase

internal actual class DriverFactory {
    actual inline fun <reified T : RoomDatabase> createBuilder(
        name: String,
        isCache: Boolean,
    ): RoomDatabase.Builder<T> {
        // TODO: Implementation for Linux
        return Room.inMemoryDatabaseBuilder()
    }

    actual fun deleteDatabase(name: String) {
//        DatabaseFileContext.deleteDatabase(name, null)
    }
}
