package dev.dimension.flare.data.database

import android.content.Context
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

internal actual class DriverFactory(
    private val context: Context,
) {
    actual fun createDriver(schema: SqlSchema<QueryResult.Value<Unit>>, name: String): SqlDriver {
        return AndroidSqliteDriver(schema, context, name)
    }
    actual fun deleteDatabase(name: String) {
        context.deleteDatabase(name)
    }
}
