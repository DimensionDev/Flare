package dev.dimension.flare.data.database.app

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

internal actual suspend fun SQLiteConnection.executeMigrationSql(sql: String) {
    execSQL(sql)
}
