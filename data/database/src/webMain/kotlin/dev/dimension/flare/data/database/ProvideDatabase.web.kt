package dev.dimension.flare.data.database

import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.web.WebWorkerSQLiteDriver
import org.w3c.dom.Worker

@OptIn(ExperimentalWasmJsInterop::class)
private fun jsWorker(): Worker = TODO("implement js worker for web database")

internal actual fun createSQLiteDriver(): SQLiteDriver = WebWorkerSQLiteDriver(jsWorker())
