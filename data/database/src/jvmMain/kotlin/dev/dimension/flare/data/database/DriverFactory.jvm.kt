package dev.dimension.flare.data.database

import androidx.room3.Room
import androidx.room3.RoomDatabase
import dev.dimension.flare.common.FileSystemUtilsExt
import org.koin.core.annotation.Singleton
import java.io.File

@Singleton
public actual class DriverFactory {
    internal actual inline fun <reified T : RoomDatabase> createBuilder(
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
}
