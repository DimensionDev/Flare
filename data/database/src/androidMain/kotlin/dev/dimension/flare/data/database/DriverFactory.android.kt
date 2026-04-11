package dev.dimension.flare.data.database

import android.content.Context
import androidx.room3.Room
import androidx.room3.RoomDatabase
import org.koin.core.annotation.Singleton
import java.io.File

@Singleton
public actual class DriverFactory(
    private val context: Context,
) {
    internal actual inline fun <reified T : RoomDatabase> createBuilder(
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
}
