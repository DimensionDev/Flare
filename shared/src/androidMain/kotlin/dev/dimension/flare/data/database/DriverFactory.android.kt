package dev.dimension.flare.data.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

internal actual class DriverFactory(
    private val context: Context,
) {
    actual inline fun <reified T : RoomDatabase> createBuilder(name: String): RoomDatabase.Builder<T> {
        val appContext = context.applicationContext
        val dbFile = appContext.getDatabasePath(name)
        return Room.databaseBuilder<T>(
            context = appContext,
            name = dbFile.absolutePath,
        )
    }

    actual fun deleteDatabase(name: String) {
        context.deleteDatabase(name)
    }
}
