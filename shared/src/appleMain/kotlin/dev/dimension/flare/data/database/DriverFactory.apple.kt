package dev.dimension.flare.data.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import co.touchlab.sqliter.DatabaseFileContext

internal actual class DriverFactory {
    actual fun createDriver(
        schema: SqlSchema<QueryResult.Value<Unit>>,
        name: String,
    ): SqlDriver = NativeSqliteDriver(schema, name)

    actual fun deleteDatabase(name: String) {
        DatabaseFileContext.deleteDatabase(name, null)
    }
}
