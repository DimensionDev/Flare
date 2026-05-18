package dev.dimension.flare.data.database

import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

public actual fun createDatabaseDriver(): SQLiteDriver = BundledSQLiteDriver()
