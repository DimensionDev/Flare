package dev.dimension.flare.data.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.moriatsushi.koject.Provides
import com.moriatsushi.koject.Singleton

@Singleton
@Provides
actual class DriverFactory {
    actual fun createDriver(schema: SqlSchema<QueryResult.Value<Unit>>, name: String): SqlDriver {
        return NativeSqliteDriver(schema, name)
    }
}