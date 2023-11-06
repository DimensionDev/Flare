package dev.dimension.flare.data.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File

internal actual class DriverFactory {
    actual fun createDriver(schema: SqlSchema<QueryResult.Value<Unit>>, name: String): SqlDriver {
        return JdbcSqliteDriver("jdbc:sqlite:$name", schema = schema)
    }

    actual fun deleteDatabase(name: String) {
        File(name).delete()
    }
}
