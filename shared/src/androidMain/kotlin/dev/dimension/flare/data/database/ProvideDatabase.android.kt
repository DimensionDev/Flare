package dev.dimension.flare.data.database

import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.AndroidSQLiteDriver

internal actual fun provideSQLiteDriver(): SQLiteDriver = AndroidSQLiteDriver()
