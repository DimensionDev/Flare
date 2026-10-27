package dev.dimension.flare.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import coil3.compose.AsyncImagePainter
import coil3.request.NullRequestData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

public val LocalShareImageLoading: androidx.compose.runtime.ProvidableCompositionLocal<ShareImageLoading?> =
    compositionLocalOf<ShareImageLoading?> { null }

@Stable
public class ShareImageLoading {
    private val images = mutableStateMapOf<Any, Boolean?>()
    private val removed = mutableSetOf<Any>()

    internal fun update(
        key: Any,
        ready: Boolean?,
    ) {
        if (key !in removed) images[key] = ready
    }

    internal fun register(key: Any) {
        removed.remove(key)
        if (!images.containsKey(key)) images[key] = null
    }

    internal fun remove(key: Any) {
        removed.add(key)
        images.remove(key)
    }

    public suspend fun awaitReady() {
        val ready =
            withTimeoutOrNull(15_000) {
                snapshotFlow { images.values.toList() }.first { states -> states.none { it == null } }
                true
            }
        check(ready == true) { "Share image loading timed out" }
        check(images.values.all { it == true }) { "Share image failed to load" }
    }
}

@Composable
internal fun shareImageObserver(model: String?): ((AsyncImagePainter.State) -> Unit)? {
    val loading = LocalShareImageLoading.current ?: return null
    if (model == null) return null
    val key = remember(model, loading) { Any() }
    DisposableEffect(key) {
        loading.register(key)
        onDispose { loading.remove(key) }
    }
    return { state ->
        when (state) {
            is AsyncImagePainter.State.Success -> loading.update(key, true)

            // The empty request waits for proxy preparation, so it is still loading.
            is AsyncImagePainter.State.Error -> loading.update(key, if (state.result.request.data == NullRequestData) null else false)

            else -> loading.update(key, null)
        }
    }
}
