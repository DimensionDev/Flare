package dev.dimension.flare.data.database

import androidx.room3.RoomDatabase
import androidx.sqlite.driver.web.WebWorkerSQLiteDriver
import kotlinx.coroutines.Dispatchers
import org.w3c.dom.Worker

internal actual fun <T : RoomDatabase> RoomDatabase.Builder<T>.platformOptions(): RoomDatabase.Builder<T> =
    this
        .setDriver(WebWorkerSQLiteDriver(jsWorker()))
        .setQueryCoroutineContext(Dispatchers.Default)

@OptIn(ExperimentalWasmJsInterop::class)
private fun jsWorker(): Worker = TODO("implement js worker for web database")
