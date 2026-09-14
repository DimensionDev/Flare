@file:OptIn(dev.dimension.compose.nativekit.LowLevelNativeKitApi::class)

package dev.dimension.compose.nativekit.lazy

import androidx.compose.runtime.Composable
import dev.dimension.compose.nativekit.LowLevelNativeKitApi
import dev.dimension.compose.nativekit.NativeKitComposable
import dev.dimension.compose.nativekit.NativeKitSubcompositionFactory
import dev.dimension.compose.nativekit.NativeKitWidget

/** Scroll direction shared by [LazyColumn] and [LazyRow]. */
public enum class LazyListOrientation {
    Vertical,
    Horizontal,
}

/** Cross-axis placement requested by a lazy collection. */
public enum class LazyCrossAxisAlignment {
    Start,
    Center,
    End,
    Stretch,
}

/** Deferred item source consumed by a renderer-provided virtual collection. */
@LowLevelNativeKitApi
public interface LazyItemProvider {
    public val itemCount: Int

    public fun key(index: Int): Any

    public fun contentType(index: Int): Any?

    /** Invalidates a cached measurement when layout-affecting content changes under a stable key. */
    public fun layoutVersion(index: Int): Any? = Unit

    @Composable
    @NativeKitComposable
    public fun Item(index: Int)
}

/** Assigns renderer-safe identities to business keys for one renderer session. */
internal class LazyItemIdentityRegistry {
    private var idsByKey = mutableMapOf<Any, Long>()
    private var keysById = mutableMapOf<Long, Any>()
    private var nextId: Long = 0L

    fun idFor(key: Any): Long {
        idsByKey[key]?.let { return it }
        check(nextId < Long.MAX_VALUE) {
            "A lazy-list identity session exhausted the available renderer identities."
        }
        val id = nextId++
        idsByKey[key] = id
        keysById[id] = key
        return id
    }

    /** Resolves a renderer identity without consulting a possibly newer provider generation. */
    fun keyFor(id: Long): Any? = keysById[id]

    fun removeMissingKeys(provider: LazyItemProvider) {
        if (idsByKey.isEmpty()) return
        val retainedIdsByKey = mutableMapOf<Any, Long>()
        val retainedKeysById = mutableMapOf<Long, Any>()
        repeat(provider.itemCount) { index ->
            val key = provider.key(index)
            idsByKey[key]?.let { id ->
                retainedIdsByKey[key] = id
                retainedKeysById[id] = key
            }
        }
        idsByKey = retainedIdsByKey
        keysById = retainedKeysById
    }
}

/** Atomic model handed from the NativeKit composition to one platform collection renderer. */
@LowLevelNativeKitApi
public data class LazyCollectionModel(
    public val orientation: LazyListOrientation,
    public val spacing: Float,
    public val crossAxisAlignment: LazyCrossAxisAlignment,
    public val itemProvider: LazyItemProvider,
    public val subcompositions: NativeKitSubcompositionFactory,
    public val state: LazyListState,
)

/** Renderer seam implemented by the platform lazy-list adapters. */
@LowLevelNativeKitApi
public interface LazyCollectionWidget : NativeKitWidget {
    public fun setModel(model: LazyCollectionModel)
}
