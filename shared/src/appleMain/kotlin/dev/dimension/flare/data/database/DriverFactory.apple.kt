package dev.dimension.flare.data.database

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.NativeSQLiteDriver
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

internal actual class DriverFactory {
    actual inline fun <reified T : RoomDatabase> createBuilder(name: String): RoomDatabase.Builder<T> {
        val dbFilePath = databaseDirPath() + "/$name"
        return Room.databaseBuilder<T>(
            name = dbFilePath,
        )
    }

    actual fun deleteDatabase(name: String) {
//        DatabaseFileContext.deleteDatabase(name, null)
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

    actual fun createSQLiteDriver(): SQLiteDriver = NativeSQLiteDriver()
}
