package dev.dimension.flare.data.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File
import java.sql.DriverManager

internal actual class DriverFactory {
    init {
        DriverManager.registerDriver(org.sqlite.JDBC())
        "${System.getProperty("user.home")}/flare/".let {
            File(it).mkdirs()
        }
    }

    actual fun createDriver(
        schema: SqlSchema<QueryResult.Value<Unit>>,
        name: String,
    ): SqlDriver = JdbcSqliteDriver("jdbc:sqlite:${System.getProperty("user.home")}/flare/$name", schema = schema)

    actual fun deleteDatabase(name: String) {
        File(name).delete()
    }
}
