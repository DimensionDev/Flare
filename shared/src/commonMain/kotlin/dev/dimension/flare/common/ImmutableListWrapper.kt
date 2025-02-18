package dev.dimension.flare.common

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList

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
