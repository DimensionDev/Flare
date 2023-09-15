package dev.dimension.flare.data.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema

expect class DriverFactory {
    fun createDriver(schema: SqlSchema<QueryResult.Value<Unit>>, name: String): SqlDriver
}