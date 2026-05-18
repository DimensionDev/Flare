package dev.dimension.flare.data.database.app

import androidx.sqlite.SQLiteConnection

internal expect suspend fun SQLiteConnection.executeMigrationSql(sql: String)
