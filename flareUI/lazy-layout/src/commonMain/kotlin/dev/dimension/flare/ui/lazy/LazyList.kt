@file:OptIn(dev.dimension.flare.ui.LowLevelFlareApi::class)

package dev.dimension.flare.ui.lazy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import dev.dimension.flare.ui.EmitFlareWidget
import dev.dimension.flare.ui.FlareModifier
import dev.dimension.flare.ui.FlareUiComposable
import dev.dimension.flare.ui.foundation.HorizontalAlignment
import dev.dimension.flare.ui.foundation.VerticalAlignment
import dev.dimension.flare.ui.rememberFlareSubcompositionFactory

/**
 * A vertically scrolling collection which composes item content only while its native cell is
 * realized. Every item key must be unique and stable across updates.
 *
 * The list's main axis must receive a bounded size from its parent or [modifier].
 */
@Composable
@FlareUiComposable
public fun LazyColumn(
    modifier: FlareModifier = FlareModifier.None,
    state: LazyListState = rememberLazyListState(),
    spacing: Float = 0f,
    horizontalAlignment: HorizontalAlignment = HorizontalAlignment.Stretch,
    content: LazyListScope.() -> Unit,
) {
    LazyList(
        orientation = LazyListOrientation.Vertical,
        modifier = modifier,
        state = state,
        spacing = spacing,
        crossAxisAlignment = horizontalAlignment.toLazyAlignment(),
        content = content,
    )
}

/**
 * A horizontally scrolling collection which composes item content only while its native cell is
 * realized. Every item key must be unique and stable across updates.
 *
 * The list's main axis must receive a bounded size from its parent or [modifier].
 */
@Composable
@FlareUiComposable
public fun LazyRow(
    modifier: FlareModifier = FlareModifier.None,
    state: LazyListState = rememberLazyListState(),
    spacing: Float = 0f,
    verticalAlignment: VerticalAlignment = VerticalAlignment.Stretch,
    content: LazyListScope.() -> Unit,
) {
    LazyList(
        orientation = LazyListOrientation.Horizontal,
        modifier = modifier,
        state = state,
        spacing = spacing,
        crossAxisAlignment = verticalAlignment.toLazyAlignment(),
        content = content,
    )
}

@Composable
@FlareUiComposable
private fun LazyList(
    orientation: LazyListOrientation,
    modifier: FlareModifier,
    state: LazyListState,
    spacing: Float,
    crossAxisAlignment: LazyCrossAxisAlignment,
    content: LazyListScope.() -> Unit,
) {
    require(spacing.isFinite() && spacing >= 0f) {
        "Lazy list spacing must be a finite, non-negative value."
    }
    val scope = IntervalLazyListScope().apply(content)
    val saveableStateHolder = rememberSaveableStateHolder()
    val saveableKeys = remember { LazySaveableKeyRegistry() }
    val itemProvider =
        SaveableLazyItemProvider(
            delegate = scope.build(),
            stateHolder = saveableStateHolder,
            stateKeys = saveableKeys,
        )
    SideEffect {
        if (itemProvider.itemCount <= MAX_EAGER_SAVEABLE_KEY_PRUNE_ITEMS) {
            saveableKeys.removeMissingKeys(itemProvider, saveableStateHolder)
        }
    }
    val model =
        LazyCollectionModel(
            orientation = orientation,
            spacing = spacing,
            crossAxisAlignment = crossAxisAlignment,
            itemProvider = itemProvider,
            subcompositions = rememberFlareSubcompositionFactory(),
            state = state,
        )
    EmitFlareWidget(
        componentType = LazyCollectionWidget::class,
        modifier = modifier,
        update = {
            set(model, LazyCollectionWidget::setModel)
        },
    )
}

private class SaveableLazyItemProvider(
    private val delegate: LazyItemProvider,
    private val stateHolder: SaveableStateHolder,
    private val stateKeys: LazySaveableKeyRegistry,
) : LazyItemProvider by delegate {
    @Composable
    @FlareUiComposable
    override fun Item(index: Int) {
        val stateKey = stateKeys.idFor(delegate.key(index))
        stateHolder.SaveableStateProvider(stateKey) {
            delegate.Item(index)
        }
    }
}

private class LazySaveableKeyRegistry {
    private val stateIds = mutableMapOf<Any, Long>()
    private var nextStateId: Long = 0L

    fun idFor(key: Any): Long = stateIds.getOrPut(key) { nextStateId++ }

    fun removeMissingKeys(
        provider: LazyItemProvider,
        stateHolder: SaveableStateHolder,
    ) {
        if (stateIds.isEmpty()) return
        val retainedKeys = mutableSetOf<Any>()
        repeat(provider.itemCount) { index ->
            val key = provider.key(index)
            if (key in stateIds) retainedKeys += key
        }
        val removed = stateIds.keys - retainedKeys
        removed.forEach { key ->
            stateHolder.removeState(checkNotNull(stateIds.remove(key)))
        }
    }
}

private const val MAX_EAGER_SAVEABLE_KEY_PRUNE_ITEMS: Int = 1_000

private fun HorizontalAlignment.toLazyAlignment(): LazyCrossAxisAlignment =
    when (this) {
        HorizontalAlignment.Start -> LazyCrossAxisAlignment.Start
        HorizontalAlignment.Center -> LazyCrossAxisAlignment.Center
        HorizontalAlignment.End -> LazyCrossAxisAlignment.End
        HorizontalAlignment.Stretch -> LazyCrossAxisAlignment.Stretch
    }

private fun VerticalAlignment.toLazyAlignment(): LazyCrossAxisAlignment =
    when (this) {
        VerticalAlignment.Top -> LazyCrossAxisAlignment.Start
        VerticalAlignment.Center -> LazyCrossAxisAlignment.Center
        VerticalAlignment.Bottom -> LazyCrossAxisAlignment.End
        VerticalAlignment.Stretch -> LazyCrossAxisAlignment.Stretch
    }
