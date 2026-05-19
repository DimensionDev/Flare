package dev.dimension.flare.data.database

import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.web.WebWorkerSQLiteDriver
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.js
import org.w3c.dom.Worker

public actual fun createDatabaseDriver(): SQLiteDriver =
    WebWorkerSQLiteDriver(createSQLiteWorker())

@OptIn(ExperimentalWasmJsInterop::class)
private fun createSQLiteWorker(): Worker =
    js("new Worker(new URL('@androidx/sqlite-web-worker/worker.js', import.meta.url), { type: 'module' })")
