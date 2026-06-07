package dev.dimension.flare.data.database

import androidx.room3.RoomDatabase
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public expect class DriverFactory {
    public inline fun <reified T : RoomDatabase> createBuilder(
        name: String,
        isCache: Boolean = false,
    ): RoomDatabase.Builder<T>

    public fun deleteDatabase(
        name: String,
        isCache: Boolean,
    )
}
