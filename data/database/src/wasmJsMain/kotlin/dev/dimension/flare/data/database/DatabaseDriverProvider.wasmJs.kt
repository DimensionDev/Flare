package dev.dimension.flare.data.database

import androidx.sqlite.SQLiteDriver

public actual fun createDatabaseDriver(): SQLiteDriver =
    error("Web database support requires an explicit SQLiteDriver.")
