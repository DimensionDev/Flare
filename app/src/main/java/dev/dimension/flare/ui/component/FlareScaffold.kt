package dev.dimension.flare.ui.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * <a href="https://material.io/design/layout/understanding-layout.html" class="external" target="_blank">Material Design layout</a>.
 *
 * Scaffold implements the basic material design visual layout structure.
 *
 * This component provides API to put together several material components to construct your
 * screen, by ensuring proper layout strategy for them and collecting necessary data so these
 * components will work together correctly.
 *
 * Simple example of a Scaffold with [SmallTopAppBar], [FloatingActionButton]:
 *
 * @sample androidx.compose.material3.samples.SimpleScaffoldWithTopBar
 *
 * To show a [Snackbar], use [SnackbarHostState.showSnackbar].
 *
 * @sample androidx.compose.material3.samples.ScaffoldWithSimpleSnackbar
 *
 * @param modifier the [Modifier] to be applied to this scaffold
 * @param topBar top app bar of the screen, typically a [SmallTopAppBar]
 * @param bottomBar bottom bar of the screen, typically a [NavigationBar]
 * @param snackbarHost component to host [Snackbar]s that are pushed to be shown via
 * [SnackbarHostState.showSnackbar], typically a [SnackbarHost]
 * @param floatingActionButton Main action button of the screen, typically a [FloatingActionButton]
 * @param floatingActionButtonPosition position of the FAB on the screen. See [FabPosition].
 * @param containerColor the color used for the background of this scaffold. Use [Color.Transparent]
 * to have no color.
 * @param contentColor the preferred color for content inside this scaffold. Defaults to either the
 * matching content color for [containerColor], or to the current [LocalContentColor] if
 * [containerColor] is not a color from the theme.
 * @param contentWindowInsets window insets to be passed to [content] slot via [PaddingValues]
 * params. Scaffold will take the insets into account from the top/bottom only if the [topBar]/
 * [bottomBar] are not present, as the scaffold expect [topBar]/[bottomBar] to handle insets
 * instead. Any insets consumed by other insets padding modifiers or [consumeWindowInsets] on a
 * parent layout will be excluded from [contentWindowInsets].
 * @param content content of the screen. The lambda receives a [PaddingValues] that should be
 * applied to the content root via [Modifier.padding] and [Modifier.consumeWindowInsets] to
 * properly offset top and bottom bars. If using [Modifier.verticalScroll], apply this modifier to
 * the child of the scroll, and not on the scroll itself.
 */
@Composable
fun FlareScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    containerColor: Color = MaterialTheme.colorScheme.background,
    contentColor: Color = contentColorFor(containerColor),
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets.add(WindowInsets(bottom = LocalBottomBarHeight.current)),
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier,
        topBar,
        bottomBar,
        snackbarHost,
        floatingActionButton,
        floatingActionButtonPosition,
        containerColor,
        contentColor,
        contentWindowInsets,
        content,
    )
}
