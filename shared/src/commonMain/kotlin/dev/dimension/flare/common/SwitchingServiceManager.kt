package dev.dimension.flare.common

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class SwitchingServiceManager<C, S : AutoCloseable>(
    credentialFlow: Flow<C?>,
    parentScope: CoroutineScope,
    private val createService: suspend (C) -> S,
) {
    private class Entry<S : AutoCloseable>(
        val service: S,
        var refCount: Int = 0,
        var acceptingNewCalls: Boolean = true,
        val drained: CompletableDeferred<Unit> = CompletableDeferred(),
    )

    private val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob())
    private val mutex = Mutex()
    private val current = MutableStateFlow<Entry<S>?>(null)

    init {
        scope.launch {
            credentialFlow.collect { credential ->
                val newEntry = credential?.let { Entry(createService(it)) }

                val retired =
                    mutex.withLock {
                        val old = current.value

                        if (old != null) {
                            old.acceptingNewCalls = false
                            if (old.refCount == 0) {
                                old.drained.complete(Unit)
                            }
                        }

                        current.value = newEntry
                        old
                    }

                if (retired != null) {
                    scope.launch {
                        retired.drained.await()
                        retired.service.close()
                    }
                }
            }
        }
    }

    suspend fun <T> withService(block: suspend (S) -> T): T {
        while (true) {
            val snapshot = current.value ?: current.filterNotNull().first()

            val acquired =
                mutex.withLock {
                    val latest = current.value
                    if (latest !== snapshot || !snapshot.acceptingNewCalls) {
                        false
                    } else {
                        snapshot.refCount += 1
                        true
                    }
                }

            if (!acquired) {
                continue
            }

            try {
                return block(snapshot.service)
            } finally {
                val shouldClose =
                    mutex.withLock {
                        snapshot.refCount -= 1
                        !snapshot.acceptingNewCalls && snapshot.refCount == 0
                    }

                if (shouldClose) {
                    snapshot.drained.complete(Unit)
                }
            }
        }
    }
}
