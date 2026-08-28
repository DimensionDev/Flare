package dev.dimension.flare.ui.lazy

import androidx.compose.runtime.Composable
import dev.dimension.flare.ui.FlareSubcompositionFactory
import dev.dimension.flare.ui.FlareUiComposable
import dev.dimension.flare.ui.FlareWidget
import dev.dimension.flare.ui.LowLevelFlareApi

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
@LowLevelFlareApi
public interface LazyItemProvider {
    public val itemCount: Int

    public fun key(index: Int): Any

    public fun contentType(index: Int): Any?

    /** Invalidates a cached measurement when layout-affecting content changes under a stable key. */
    public fun layoutVersion(index: Int): Any? = Unit

    @Composable
    @FlareUiComposable
    public fun Item(index: Int)
}

/** Atomic model handed from the Flare composition to one platform collection renderer. */
@LowLevelFlareApi
public data class LazyCollectionModel(
    public val orientation: LazyListOrientation,
    public val spacing: Float,
    public val crossAxisAlignment: LazyCrossAxisAlignment,
    public val itemProvider: LazyItemProvider,
    public val subcompositions: FlareSubcompositionFactory,
    public val state: LazyListState,
)

/** Renderer seam implemented by the platform lazy-list adapters. */
@LowLevelFlareApi
public interface LazyCollectionWidget : FlareWidget {
    public fun setModel(model: LazyCollectionModel)
}
