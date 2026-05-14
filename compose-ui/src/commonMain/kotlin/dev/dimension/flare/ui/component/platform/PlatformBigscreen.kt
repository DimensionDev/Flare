package dev.dimension.flare.ui.component.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalMediaQueryApi
import androidx.compose.ui.derivedMediaQuery
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A lower bound for a size class with Medium width in dp. */
private const val WIDTH_DP_MEDIUM_LOWER_BOUND: Int = 600

/** A lower bound for a size class with Expanded width in dp. */
private const val WIDTH_DP_EXPANDED_LOWER_BOUND: Int = 840

/** A lower bound for a size class with Large width in dp. */
private const val WIDTH_DP_LARGE_LOWER_BOUND: Int = 1024

/** A lower bound for a size class width Extra Large width in dp. */
private const val WIDTH_DP_EXTRA_LARGE_LOWER_BOUND: Int = 1200

public enum class WindowSizeClass { Compact, Medium, Expanded, Large, ExtraLarge }

public val LocalWindowSizeClass: ProvidableCompositionLocal<WindowSizeClass> =
    compositionLocalOf {
        WindowSizeClass.Compact
    }

@OptIn(ExperimentalMediaQueryApi::class)
@Composable
public fun calculateWindowSizeClass(): State<WindowSizeClass> {
    val isCompat by derivedMediaQuery {
        windowWidth < WIDTH_DP_MEDIUM_LOWER_BOUND.dp
    }
    val isExpanded by derivedMediaQuery {
        windowWidth >= WIDTH_DP_EXPANDED_LOWER_BOUND.dp
    }
    val isLarge by derivedMediaQuery {
        windowWidth >= WIDTH_DP_LARGE_LOWER_BOUND.dp
    }
    val isExtraLarge by derivedMediaQuery {
        windowWidth >= WIDTH_DP_EXTRA_LARGE_LOWER_BOUND.dp
    }
    return remember {
        derivedStateOf {
            when {
                isExtraLarge -> WindowSizeClass.ExtraLarge
                isLarge -> WindowSizeClass.Large
                isExpanded -> WindowSizeClass.Expanded
                isCompat -> WindowSizeClass.Compact
                else -> WindowSizeClass.Medium
            }
        }
    }
}

@OptIn(ExperimentalMediaQueryApi::class)
@Composable
public fun isBigScreen(): Boolean =
    LocalWindowSizeClass.current == WindowSizeClass.Expanded ||
        LocalWindowSizeClass.current == WindowSizeClass.Large ||
        LocalWindowSizeClass.current == WindowSizeClass.ExtraLarge

@OptIn(ExperimentalMediaQueryApi::class)
@Composable
public fun isCompatScreen(): Boolean = LocalWindowSizeClass.current == WindowSizeClass.Compact

@OptIn(ExperimentalMediaQueryApi::class)
@Composable
public fun isNormalScreen(): Boolean = LocalWindowSizeClass.current == WindowSizeClass.Medium

@OptIn(ExperimentalMediaQueryApi::class)
@Composable
public fun adaptiveSideContentSize(): State<Dp> {
    val isLargerScreen =
        LocalWindowSizeClass.current == WindowSizeClass.Large ||
            LocalWindowSizeClass.current == WindowSizeClass.ExtraLarge
    return remember(isLargerScreen) {
        derivedStateOf {
            if (isLargerScreen) {
                432.dp
            } else {
                332.dp
            }
        }
    }
}
