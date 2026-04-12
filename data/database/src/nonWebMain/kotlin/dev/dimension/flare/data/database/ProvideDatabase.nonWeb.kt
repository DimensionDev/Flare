package dev.dimension.flare.data.database

import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

internal actual fun createSQLiteDriver(): SQLiteDriver = BundledSQLiteDriver()
