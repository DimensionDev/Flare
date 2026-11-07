package dev.dimension.flare.rust

import java.lang.ref.WeakReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

// Compile with bindings/Initialization.kt instead of generated JNI bindings to hold back completion.
data class InitConfig(val androidFilesDir: String? = null)
class InitError : Exception()
interface InitializationObserver {
    fun onInitialized()
    fun onFailed(error: InitError)
}

private val observers = mutableListOf<InitializationObserver>()

fun beginInitialize(config: InitConfig, observer: InitializationObserver) {
    check(config.androidFilesDir == null)
    observers += observer
}

private fun createConsumer(scope: CoroutineScope): Pair<Job, WeakReference<Any>> {
    val payload = Any()
    val reference = WeakReference(payload)
    val job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
        initialize(InitConfig())
        check(payload === reference.get())
    }
    return job to reference
}

private suspend fun cancelledConsumer(): WeakReference<Any> {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    val (job, reference) = createConsumer(scope)
    check(observers.size == 1)
    job.cancelAndJoin()
    scope.cancel()
    return reference
}

private fun collected(reference: WeakReference<Any>): Boolean {
    repeat(100) {
        System.gc()
        Thread.sleep(10)
        if (reference.get() == null) return true
    }
    return false
}

fun main(): Unit = runBlocking {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    scope.cancel()
    scope.launch(start = CoroutineStart.UNDISPATCHED) { initialize(InitConfig()) }.join()
    check(observers.isEmpty()) { "an already cancelled caller started native initialization" }

    val reference = cancelledConsumer()
    check(collected(reference)) { "the native observer retains the cancelled caller's payload" }
    observers.removeAt(0).onInitialized()

    cancelledConsumer()
    observers.removeAt(0).onFailed(InitError())

    val success = async(start = CoroutineStart.UNDISPATCHED) { initialize(InitConfig()) }
    observers.removeAt(0).onInitialized()
    success.await()

    val error = InitError()
    val failure = async(start = CoroutineStart.UNDISPATCHED) { runCatching { initialize(InitConfig()) } }
    observers.removeAt(0).onFailed(error)
    check(failure.await().exceptionOrNull() === error)
    check(observers.isEmpty())
    println("Kotlin initialization waiter: cancellation, retained callers and completion passed.")
}
