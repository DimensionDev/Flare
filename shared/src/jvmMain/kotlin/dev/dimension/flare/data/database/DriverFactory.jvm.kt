package dev.dimension.flare.data.database

import androidx.room.Room
import androidx.room.RoomDatabase
import dev.dimension.flare.common.FileSystemUtilsExt

internal actual class DriverFactory {
    actual inline fun <reified T : RoomDatabase> createBuilder(
        name: String,
        isCache: Boolean,
    ): RoomDatabase.Builder<T> {
        val dbFile =
            if (isCache) {
                FileSystemUtilsExt.flareCacheDirectory()
            } else {
                FileSystemUtilsExt.flareDirectory()
            }
        return Room.databaseBuilder<T>(
            name = dbFile.absolutePath,
        )
    }

    actual fun deleteDatabase(name: String) {
    }
}
