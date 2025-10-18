package dev.dimension.flare.data.database

import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.stringWithString

internal actual class DriverFactory {
    actual inline fun <reified T : RoomDatabase> createBuilder(
        name: String,
        isCache: Boolean,
    ): RoomDatabase.Builder<T> {
        val dbFilePath = databaseDirPath() + "/$name"
        return Room.databaseBuilder<T>(
            name = dbFilePath,
        )
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun deleteDatabase(
        name: String,
        isCache: Boolean,
    ) {
        val dbFilePath = databaseDirPath() + "/$name"
        val dbFile = platform.Foundation.NSString.stringWithString(dbFilePath)
        val fileManager = NSFileManager.defaultManager()
        if (fileManager.fileExistsAtPath(dbFile)) {
            fileManager.removeItemAtPath(dbFile, null)
        }
    }

    internal fun databaseDirPath(): String = iosDirPath("databases")

    @OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.UnsafeNumber::class)
    internal fun iosDirPath(folder: String): String {
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
