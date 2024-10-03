package dev.dimension.flare.data.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import java.io.File

internal actual class DriverFactory(
    private val context: Context,
) {
    actual inline fun <reified T : RoomDatabase> createBuilder(
        name: String,
        isCache: Boolean,
    ): RoomDatabase.Builder<T> {
        val appContext = context.applicationContext
        val dbFile =
            if (isCache) {
                File(appContext.cacheDir, name)
            } else {
                appContext.getDatabasePath(name)
            }
        return Room.databaseBuilder<T>(
            context = appContext,
            name = dbFile.absolutePath,
        )
    }

    actual fun deleteDatabase(name: String) {
        context.deleteDatabase(name)
    }
}
