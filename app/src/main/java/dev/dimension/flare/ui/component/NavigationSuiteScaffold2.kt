package dev.dimension.flare.ui.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.ExperimentalMaterial3AdaptiveNavigationSuiteApi
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteColors
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItemColors
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScope
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.collection.MutableVector
import androidx.compose.runtime.collection.mutableVectorOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

val LocalBottomBarHeight = androidx.compose.runtime.staticCompositionLocalOf<Dp> { 80.dp }

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@ExperimentalMaterial3AdaptiveNavigationSuiteApi
@Composable
fun NavigationSuiteScaffold2(
    navigationSuiteItems: NavigationSuiteScope.() -> Unit,
    modifier: Modifier = Modifier,
    layoutType: NavigationSuiteType =
        NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfo()),
    navigationSuiteColors: NavigationSuiteColors = NavigationSuiteDefaults.colors(),
    containerColor: Color = MaterialTheme.colorScheme.background,
    contentColor: Color = contentColorFor(containerColor),
    railHeader: @Composable (ColumnScope.() -> Unit)? = null,
    drawerHeader: @Composable (ColumnScope.() -> Unit)? = null,
    footerItems: NavigationSuiteScope.() -> Unit = {},
    content: @Composable () -> Unit = {},
) {
    val bottomBarHeightPx =
        with(LocalDensity.current) {
            val navigationBar = WindowInsets.navigationBars
            remember(navigationBar) {
                80.0.dp.roundToPx().toFloat() + navigationBar.getBottom(this)
            }
        }
    var bottomBarOffsetHeightPx by remember { mutableFloatStateOf(0f) }
    val nestedScrollConnection =
        remember(bottomBarHeightPx) {
            object : NestedScrollConnection {
                override fun onPreScroll(
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    val delta = available.y
                    val newOffset = bottomBarOffsetHeightPx + delta
                    bottomBarOffsetHeightPx = newOffset.coerceIn(-bottomBarHeightPx, 0f)
                    return Offset.Zero
                }
            }
        }
    Surface(
        modifier =
            modifier
                .nestedScroll(nestedScrollConnection),
        color = containerColor,
        contentColor = contentColor,
    ) {
        val scope by rememberStateOfItems(navigationSuiteItems)
        val footerScope by rememberStateOfItems(footerItems)
        Row {
            if (layoutType == NavigationSuiteType.NavigationRail) {
                NavigationRail(
                    header = railHeader,
                    containerColor = navigationSuiteColors.navigationRailContainerColor,
                    contentColor = navigationSuiteColors.navigationRailContentColor,
                ) {
                    scope.itemList.forEach {
                        NavigationRailItem(
                            modifier = it.modifier,
                            selected = it.selected,
                            onClick = it.onClick,
                            icon = { NavigationItemIcon(icon = it.icon, badge = it.badge) },
                            enabled = it.enabled,
                            label = it.label,
                            alwaysShowLabel = it.alwaysShowLabel,
                            colors =
                                it.colors?.navigationRailItemColors
                                    ?: NavigationRailItemDefaults.colors(),
                            interactionSource = it.interactionSource,
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    footerScope.itemList.forEach {
                        NavigationRailItem(
                            modifier = it.modifier,
                            selected = it.selected,
                            onClick = it.onClick,
                            icon = { NavigationItemIcon(icon = it.icon, badge = it.badge) },
                            enabled = it.enabled,
                            label = it.label,
                            alwaysShowLabel = it.alwaysShowLabel,
                            colors =
                                it.colors?.navigationRailItemColors
                                    ?: NavigationRailItemDefaults.colors(),
                            interactionSource = it.interactionSource,
                        )
                    }
                }
            } else if (layoutType == NavigationSuiteType.NavigationDrawer) {
                PermanentDrawerSheet(
                    modifier =
                        Modifier
                            .width(240.dp)
                            .padding(horizontal = 8.dp),
                    drawerContainerColor = navigationSuiteColors.navigationDrawerContainerColor,
                    drawerContentColor = navigationSuiteColors.navigationDrawerContentColor,
                ) {
                    drawerHeader?.invoke(this)
                    drawerHeader?.let {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    scope.itemList.forEach {
                        NavigationDrawerItem(
                            modifier = it.modifier,
                            selected = it.selected,
                            onClick = it.onClick,
                            icon = it.icon,
                            badge = it.badge,
                            label = { it.label?.invoke() ?: Text("") },
                            colors =
                                it.colors?.navigationDrawerItemColors
                                    ?: NavigationDrawerItemDefaults.colors(),
                            interactionSource = it.interactionSource,
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    footerScope.itemList.forEach {
                        NavigationDrawerItem(
                            modifier = it.modifier,
                            selected = it.selected,
                            onClick = it.onClick,
                            icon = it.icon,
                            badge = it.badge,
                            label = { it.label?.invoke() ?: Text("") },
                            colors =
                                it.colors?.navigationDrawerItemColors
                                    ?: NavigationDrawerItemDefaults.colors(),
                            interactionSource = it.interactionSource,
                        )
                    }
                }
            }
            Box {
                CompositionLocalProvider(
                    LocalBottomBarHeight provides
                        if (layoutType == NavigationSuiteType.NavigationBar) {
                            80.dp
                        } else {
                            0.dp
                        },
                ) {
                    content.invoke()
                }
                if (layoutType == NavigationSuiteType.NavigationBar) {
                    NavigationBar(
                        containerColor = navigationSuiteColors.navigationBarContainerColor,
                        contentColor = navigationSuiteColors.navigationBarContentColor,
                        modifier =
                            Modifier
                                .align(Alignment.BottomCenter)
                                .offset { IntOffset(x = 0, y = -bottomBarOffsetHeightPx.roundToInt()) },
                    ) {
                        scope.itemList.forEach {
                            NavigationBarItem(
                                modifier = it.modifier,
                                selected = it.selected,
                                onClick = it.onClick,
                                icon = { NavigationItemIcon(icon = it.icon, badge = it.badge) },
                                enabled = it.enabled,
                                label = it.label,
                                alwaysShowLabel = it.alwaysShowLabel,
                                colors =
                                    it.colors?.navigationBarItemColors
                                        ?: NavigationBarItemDefaults.colors(),
                                interactionSource = it.interactionSource,
                            )
                        }
                    }
                }
            }
        }
    }
}

private interface NavigationSuiteItemProvider {
    val itemsCount: Int
    val itemList: MutableVector<NavigationSuiteItem>
}

@OptIn(ExperimentalMaterial3AdaptiveNavigationSuiteApi::class)
private class NavigationSuiteItem(
    val selected: Boolean,
    val onClick: () -> Unit,
    val icon: @Composable () -> Unit,
    val modifier: Modifier,
    val enabled: Boolean,
    val label: @Composable (() -> Unit)?,
    val alwaysShowLabel: Boolean,
    val badge: (@Composable () -> Unit)?,
    val colors: NavigationSuiteItemColors?,
    val interactionSource: MutableInteractionSource?,
)

@OptIn(ExperimentalMaterial3AdaptiveNavigationSuiteApi::class)
private class NavigationSuiteScopeImpl :
    NavigationSuiteScope,
    NavigationSuiteItemProvider {
    override fun item(
        selected: Boolean,
        onClick: () -> Unit,
        icon: @Composable () -> Unit,
        modifier: Modifier,
        enabled: Boolean,
        label: @Composable (() -> Unit)?,
        alwaysShowLabel: Boolean,
        badge: (@Composable () -> Unit)?,
        colors: NavigationSuiteItemColors?,
        interactionSource: MutableInteractionSource?,
    ) {
        itemList.add(
            NavigationSuiteItem(
                selected = selected,
                onClick = onClick,
                icon = icon,
                modifier = modifier,
                enabled = enabled,
                label = label,
                alwaysShowLabel = alwaysShowLabel,
                badge = badge,
                colors = colors,
                interactionSource = interactionSource,
            ),
        )
    }

    override val itemList: MutableVector<NavigationSuiteItem> = mutableVectorOf()

    override val itemsCount: Int
        get() = itemList.size
}

@OptIn(ExperimentalMaterial3AdaptiveNavigationSuiteApi::class)
@Composable
private fun rememberStateOfItems(content: NavigationSuiteScope.() -> Unit): State<NavigationSuiteItemProvider> {
    val latestContent = rememberUpdatedState(content)
    return remember {
        derivedStateOf { NavigationSuiteScopeImpl().apply(latestContent.value) }
    }
}

@Composable
private fun NavigationItemIcon(
    icon: @Composable () -> Unit,
    badge: (@Composable () -> Unit)? = null,
) {
    if (badge != null) {
        BadgedBox(badge = { badge.invoke() }) {
            icon()
        }
    } else {
        icon()
    }
}
