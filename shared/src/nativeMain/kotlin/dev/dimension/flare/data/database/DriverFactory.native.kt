package dev.dimension.flare.data.database

import app.cash.molecule.DisplayLinkClock
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import co.touchlab.sqliter.DatabaseFileContext
import com.moriatsushi.koject.Provides
import com.moriatsushi.koject.Singleton
import kotlinx.coroutines.CoroutineScope

@Singleton
@Provides
internal actual class DriverFactory {
    actual fun createDriver(schema: SqlSchema<QueryResult.Value<Unit>>, name: String): SqlDriver {
        CoroutineScope(DisplayLinkClock)
        return NativeSqliteDriver(schema, name)
    }

    actual fun deleteDatabase(name: String) {
        DatabaseFileContext.deleteDatabase(name, null)
    }
}
