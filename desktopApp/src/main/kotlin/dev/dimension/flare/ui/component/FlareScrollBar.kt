package dev.dimension.flare.ui.component

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import dev.dimension.flare.LocalWindowPadding
import io.github.composefluent.component.Scrollbar
import io.github.composefluent.component.ScrollbarAdapter
import io.github.composefluent.component.ScrollbarContainer

@Composable
internal fun FlareScrollBar(
    adapter: ScrollbarAdapter,
    modifier: Modifier = Modifier,
    isVertical: Boolean = true,
    reverseLayout: Boolean = false,
    scrollbarPadding: PaddingValues = PaddingValues(),
    content: @Composable () -> Unit,
) {
    val windowPadding = LocalWindowPadding.current
    val layoutDirection = LocalLayoutDirection.current
    val insetModifier =
        if (isVertical) {
            Modifier.padding(
                top = windowPadding.calculateTopPadding(),
                end = windowPadding.calculateRightPadding(layoutDirection),
                bottom = windowPadding.calculateBottomPadding(),
            )
        } else {
            Modifier.padding(
                start = windowPadding.calculateLeftPadding(layoutDirection),
                end = windowPadding.calculateRightPadding(layoutDirection),
                bottom = windowPadding.calculateBottomPadding(),
            )
        }
    ScrollbarContainer(
        modifier = modifier,
        isVertical = isVertical,
        scrollbar = {
            Box(
                modifier =
                    insetModifier.padding(
                        start = scrollbarPadding.calculateLeftPadding(layoutDirection),
                        top = scrollbarPadding.calculateTopPadding(),
                        end = scrollbarPadding.calculateRightPadding(layoutDirection),
                        bottom = scrollbarPadding.calculateBottomPadding(),
                    ),
            ) {
                Scrollbar(
                    isVertical = isVertical,
                    adapter = adapter,
                    reverseLayout = reverseLayout,
                )
            }
        },
        content = content,
    )
}

@Composable
internal fun FlareScrollBar(
    state: ScrollState,
    modifier: Modifier = Modifier,
    isVertical: Boolean = true,
    reverseLayout: Boolean = false,
    scrollbarPadding: PaddingValues = PaddingValues(),
    content: @Composable () -> Unit,
) {
    FlareScrollBar(
        adapter = rememberScrollbarAdapter(state),
        modifier = modifier,
        isVertical = isVertical,
        reverseLayout = reverseLayout,
        scrollbarPadding = scrollbarPadding,
        content = content,
    )
}

@Composable
internal fun FlareScrollBar(
    state: LazyListState,
    modifier: Modifier = Modifier,
    isVertical: Boolean = true,
    reverseLayout: Boolean = false,
    scrollbarPadding: PaddingValues = PaddingValues(),
    content: @Composable () -> Unit,
) {
    FlareScrollBar(
        adapter = rememberScrollbarAdapter(state),
        modifier = modifier,
        isVertical = isVertical,
        reverseLayout = reverseLayout,
        scrollbarPadding = scrollbarPadding,
        content = content,
    )
}

@Composable
internal fun FlareScrollBar(
    state: LazyStaggeredGridState,
    modifier: Modifier = Modifier,
    isVertical: Boolean = true,
    reverseLayout: Boolean = false,
    scrollbarPadding: PaddingValues = PaddingValues(),
    content: @Composable () -> Unit,
) {
    FlareScrollBar(
        adapter = rememberScrollbarAdapter(state),
        modifier = modifier,
        isVertical = isVertical,
        reverseLayout = reverseLayout,
        scrollbarPadding = scrollbarPadding,
        content = content,
    )
}
