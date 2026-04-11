package dev.dimension.flare.data.database

import androidx.room3.Room
import androidx.room3.RoomDatabase
import org.koin.core.annotation.Singleton
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

@Singleton
public actual class DriverFactory {
    internal actual inline fun <reified T : RoomDatabase> createBuilder(
        name: String,
        isCache: Boolean,
    ): RoomDatabase.Builder<T> {
        val dbFilePath = databaseDirPath() + "/$name"
        return Room.databaseBuilder<T>(
            name = dbFilePath,
        )
    }

    public fun databaseDirPath(): String = iosDirPath("databases")

    @OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.UnsafeNumber::class)
    public fun iosDirPath(folder: String): String {
        val paths = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true)
        val documentsDirectory = paths[0] as String

        val databaseDirectory = "$documentsDirectory/$folder"

        val fileManager = NSFileManager.defaultManager()

        if (!fileManager.fileExistsAtPath(databaseDirectory)) {
            fileManager.createDirectoryAtPath(databaseDirectory, true, null, null)
        }; // Create folder

        return databaseDirectory
    }
}
