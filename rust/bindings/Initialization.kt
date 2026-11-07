package dev.dimension.flare.rust

import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine

/** Wait for the process's shared Rust application to initialize. */
public suspend fun initialize(config: InitConfig): Unit =
    suspendCancellableCoroutine { continuation ->
        val pending = AtomicReference<CancellableContinuation<Unit>?>(continuation)
        continuation.invokeOnCancellation { pending.set(null) }
        if (pending.get() == null) return@suspendCancellableCoroutine
        val observer = object : InitializationObserver {
            override fun onInitialized() {
                pending.getAndSet(null)?.resume(Unit)
            }

            override fun onFailed(error: InitError) {
                pending.getAndSet(null)?.resumeWith(Result.failure(error))
            }
        }
        try {
            beginInitialize(config, observer)
        } catch (error: Throwable) {
            pending.getAndSet(null)?.resumeWith(Result.failure(error))
        }
    }
