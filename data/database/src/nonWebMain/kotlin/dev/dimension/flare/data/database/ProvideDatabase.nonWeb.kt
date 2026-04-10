package dev.dimension.flare.data.database

import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

internal actual fun <T : RoomDatabase> RoomDatabase.Builder<T>.platformOptions(): RoomDatabase.Builder<T> {
    return this.setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
}