package dev.dimension.flare.feature.plugin.lifecycle

import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/** Keep direct reads and collectors current without an application-scope observer. */
@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
internal fun <A, B, T> derivedPluginState(
    first: StateFlow<A>,
    second: StateFlow<B>,
    transform: (A, B) -> T,
): StateFlow<T> =
    object : StateFlow<T> {
        override val value: T get() = transform(first.value, second.value)
        override val replayCache get() = listOf(value)

        override suspend fun collect(collector: FlowCollector<T>): Nothing {
            combine(first, second) { _, _ -> value }.distinctUntilChanged().collect(collector)
            awaitCancellation()
        }
    }
