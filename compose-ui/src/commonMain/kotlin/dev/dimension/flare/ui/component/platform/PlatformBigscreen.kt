package dev.dimension.flare.ui.component.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
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
private const val WIDTH_DP_LARGE_LOWER_BOUND: Int = 1200

/** A lower bound for a size class width Extra Large width in dp. */
private const val WIDTH_DP_EXTRA_LARGE_LOWER_BOUND: Int = 1600

@OptIn(ExperimentalMediaQueryApi::class)
@Composable
public fun isBigScreen(): State<Boolean> =
    derivedMediaQuery {
        windowWidth >= WIDTH_DP_EXPANDED_LOWER_BOUND.dp
    }

@OptIn(ExperimentalMediaQueryApi::class)
@Composable
public fun isCompatScreen(): State<Boolean> =
    derivedMediaQuery {
        windowWidth < WIDTH_DP_MEDIUM_LOWER_BOUND.dp
    }

@OptIn(ExperimentalMediaQueryApi::class)
@Composable
public fun isNormalScreen(): State<Boolean> =
    derivedMediaQuery {
        windowWidth >= WIDTH_DP_MEDIUM_LOWER_BOUND.dp && windowWidth < WIDTH_DP_EXPANDED_LOWER_BOUND.dp
    }

@OptIn(ExperimentalMediaQueryApi::class)
@Composable
public fun adaptiveSideContentSize(): State<Dp> {
    val isLargerScreen by derivedMediaQuery {
        when (windowWidth) {
            in 840.dp..1024.dp -> false
            else -> true
        }
    }
    return remember(isLargerScreen) {
        derivedStateOf {
            if (isLargerScreen) {
                332.dp
            } else {
                432.dp
            }
        }
    }
}
