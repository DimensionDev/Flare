package dev.dimension.flare.common

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

@OptIn(ExperimentalCoroutinesApi::class)
internal inline fun <reified T> Flow<List<Flow<T>>>.combineLatestFlowLists(): Flow<List<T>> =
    flatMapLatest { flows ->
        if (flows.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(flows) { array ->
                array.toList()
            }
        }
    }
