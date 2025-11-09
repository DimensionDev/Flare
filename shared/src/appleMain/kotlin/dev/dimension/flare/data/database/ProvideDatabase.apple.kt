package dev.dimension.flare.data.database

import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.NativeSQLiteDriver

internal actual fun provideSQLiteDriver(): SQLiteDriver = NativeSQLiteDriver()
