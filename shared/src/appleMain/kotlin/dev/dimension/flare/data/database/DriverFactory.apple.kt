package dev.dimension.flare.data.database

import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import dev.dimension.flare.data.io.AppleDataDirectories
import kotlinx.cinterop.ExperimentalForeignApi
import org.koin.core.annotation.Single
import platform.Foundation.NSFileManager
import platform.Foundation.stringWithString
import kotlin.native.HiddenFromObjC

@Single
@HiddenFromObjC
public actual class DriverFactory {
    public actual inline fun <reified T : RoomDatabase> createBuilder(
        name: String,
        isCache: Boolean,
    ): RoomDatabase.Builder<T> {
        val dbFilePath = databaseDirPath(isCache) + "/$name"
        return Room
            .databaseBuilder<T>(
                name = dbFilePath,
            ).addCallback(
                object : RoomDatabase.Callback() {
                    override suspend fun onOpen(connection: SQLiteConnection) {
                        connection.execSQL("PRAGMA journal_size_limit = 0")
                    }
                },
            )
    }

    @OptIn(ExperimentalForeignApi::class)
    public actual fun deleteDatabase(
        name: String,
        isCache: Boolean,
    ) {
        val dbFilePath = databaseDirPath(isCache) + "/$name"
        val dbFile = platform.Foundation.NSString.stringWithString(dbFilePath)
        val fileManager = NSFileManager.defaultManager()
        if (fileManager.fileExistsAtPath(dbFile)) {
            fileManager.removeItemAtPath(dbFile, null)
        }
    }

    @PublishedApi
    internal fun databaseDirPath(isCache: Boolean): String = AppleDataDirectories.databaseRootDirectory(isCache)
}
