package dev.dimension.flare.data.database

import androidx.room3.Room
import androidx.room3.RoomDatabase
import dev.dimension.flare.common.FileSystemUtilsExt
import org.koin.core.annotation.Single
import java.io.File

@Single
public actual class DriverFactory {
    public actual inline fun <reified T : RoomDatabase> createBuilder(
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

    public actual fun deleteDatabase(
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
