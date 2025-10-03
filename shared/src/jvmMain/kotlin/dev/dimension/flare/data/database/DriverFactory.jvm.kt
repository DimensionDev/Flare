package dev.dimension.flare.data.database

import androidx.room.Room
import androidx.room.RoomDatabase
import dev.dimension.flare.common.FileSystemUtilsExt
import java.io.File

internal actual class DriverFactory {
    actual inline fun <reified T : RoomDatabase> createBuilder(
        name: String,
        isCache: Boolean,
    ): RoomDatabase.Builder<T> {
        val dbFolder =
            if (isCache) {
                FileSystemUtilsExt.flareCacheDirectory()
            } else {
                FileSystemUtilsExt.flareDirectory()
            }
        val dbFile = File(dbFolder, name)
        return Room.databaseBuilder<T>(
            name = dbFile.absolutePath,
        )
    }

    actual fun deleteDatabase(
        name: String,
        isCache: Boolean,
    ) {
        val dbFolder =
            if (isCache) {
                FileSystemUtilsExt.flareCacheDirectory()
            } else {
                FileSystemUtilsExt.flareDirectory()
            }
        val dbFile = File(dbFolder, name)
        if (dbFile.exists()) {
            dbFile.delete()
        }
    }
}
