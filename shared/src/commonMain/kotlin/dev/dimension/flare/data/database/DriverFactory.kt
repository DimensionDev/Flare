package dev.dimension.flare.data.database

import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteDriver

internal expect class DriverFactory {
    inline fun <reified T : RoomDatabase> createBuilder(name: String): RoomDatabase.Builder<T>

    fun createSQLiteDriver(): SQLiteDriver

    fun deleteDatabase(name: String)
}
