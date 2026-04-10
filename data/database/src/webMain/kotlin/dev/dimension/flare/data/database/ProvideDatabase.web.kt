package dev.dimension.flare.data.database

import androidx.room3.RoomDatabase
import androidx.sqlite.driver.web.WebWorkerSQLiteDriver
import kotlinx.coroutines.Dispatchers
import org.w3c.dom.Worker

internal actual fun <T : RoomDatabase> RoomDatabase.Builder<T>.platformOptions(): RoomDatabase.Builder<T> {
    return this.setDriver(WebWorkerSQLiteDriver(jsWorker()))
        .setQueryCoroutineContext(Dispatchers.Default)
}
@OptIn(ExperimentalWasmJsInterop::class)
private fun jsWorker(): Worker =
    js(
        // TODO: implement js worker
        """
//            new Worker(new URL("sqlite-wasm-worker/worker.js", import.meta.url))
        """.trimIndent()
    )