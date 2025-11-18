package dev.dimension.flare.common

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

@Immutable
public data class ImmutableListWrapper<T : Any>(
    private val data: ImmutableList<T>,
) {
    val size: Int
        get() = data.size

    public operator fun get(index: Int): T = data[index]

    public fun indexOf(element: T): Int = data.indexOf(element)

    public fun contains(element: T): Boolean = data.contains(element)

    public fun toImmutableList(): ImmutableList<T> = data
}

internal fun <T : Any> ImmutableList<T>.toImmutableListWrapper(): ImmutableListWrapper<T> = ImmutableListWrapper(this)

@OptIn(ExperimentalCoroutinesApi::class)
internal inline fun <reified T> Flow<List<Flow<T>>>.combineLatestFlowLists(): Flow<List<T>> =
    this.flatMapLatest { innerFlows ->
        if (innerFlows.isEmpty()) {
            flowOf(emptyList<T>())
        } else {
            combine(innerFlows) { latestValuesArray ->
                latestValuesArray.toList()
            }
        }
    }
