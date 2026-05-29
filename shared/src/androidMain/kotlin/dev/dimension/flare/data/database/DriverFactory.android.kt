package dev.dimension.flare.data.database

import android.content.Context
import androidx.room3.Room
import androidx.room3.RoomDatabase
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import java.io.File

@Single
internal actual class DriverFactory(
    @Provided private val context: Context,
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

    actual fun deleteDatabase(
        name: String,
        isCache: Boolean,
    ) {
        val appContext = context.applicationContext
        val dbFile =
            if (isCache) {
                File(appContext.cacheDir, name)
            } else {
                appContext.getDatabasePath(name)
            }
        if (dbFile.exists()) {
            dbFile.delete()
        }
    }
}
