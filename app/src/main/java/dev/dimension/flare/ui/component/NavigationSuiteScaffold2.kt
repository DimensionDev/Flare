package dev.dimension.flare.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
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
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.ExperimentalMaterial3AdaptiveNavigationSuiteApi
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuite
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteColors
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItemColors
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScope
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.collection.MutableVector
import androidx.compose.runtime.collection.mutableVectorOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.xr.compose.material3.EnableXrComponentOverrides
import androidx.xr.compose.material3.ExperimentalMaterial3XrApi
import androidx.xr.compose.platform.LocalSession
import androidx.xr.compose.platform.LocalSpatialCapabilities
import androidx.xr.compose.spatial.EdgeOffset
import androidx.xr.compose.spatial.Orbiter
import androidx.xr.compose.spatial.OrbiterEdge
import androidx.xr.compose.subspace.SpatialPanel
import androidx.xr.compose.subspace.layout.SpatialRoundedCornerShape
import androidx.xr.compose.subspace.layout.SubspaceModifier
import androidx.xr.compose.subspace.layout.height
import androidx.xr.compose.subspace.layout.movable
import androidx.xr.compose.subspace.layout.resizable
import androidx.xr.compose.subspace.layout.width
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.DownLeftAndUpRightToCenter
import dev.dimension.flare.ui.component.status.FlareDividerDefaults
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val bottomBarHeight = 56.dp

val LocalBottomBarHeight = androidx.compose.runtime.staticCompositionLocalOf<Dp> { bottomBarHeight }

internal data class BottomBarSettings(
    val autoHide: Boolean = true,
    val withDivider: Boolean = true,
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3XrApi::class)
@ExperimentalMaterial3AdaptiveNavigationSuiteApi
@Composable
fun NavigationSuiteScaffold2(
    navigationSuiteItems: NavigationSuiteScope2.() -> Unit,
    secondaryItems: NavigationSuiteScope2.() -> Unit,
    modifier: Modifier = Modifier,
    drawerState: DrawerState = rememberDrawerState(DrawerValue.Closed),
    drawerGesturesEnabled: Boolean = true,
    bottomBarAutoHideEnabled: Boolean = true,
    bottomBarDividerEnabled: Boolean = true,
    layoutType: NavigationSuiteType =
        NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfo()),
    navigationSuiteColors: NavigationSuiteColors = NavigationSuiteDefaults.colors(),
    containerColor: Color = MaterialTheme.colorScheme.background,
    contentColor: Color = contentColorFor(containerColor),
    railHeader: @Composable (ColumnScope.() -> Unit)? = null,
    drawerHeader: @Composable (ColumnScope.() -> Unit)? = null,
    footerItems: NavigationSuiteScope2.() -> Unit = {},
    content: @Composable () -> Unit = {},
) {
    val coroutineScope = rememberCoroutineScope()
    val bottomBarHeightPx =
        with(LocalDensity.current) {
            val navigationBar = WindowInsets.navigationBars
            remember(navigationBar) {
                bottomBarHeight.roundToPx().toFloat() + navigationBar.getBottom(this)
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
                .let {
                    if (bottomBarAutoHideEnabled) {
                        it.nestedScroll(nestedScrollConnection)
                    } else {
                        it
                    }
                },
        color = containerColor,
        contentColor = contentColor,
    ) {
        OptionalSubspace {
            EnableXrComponentOverrides {
                val scope by rememberStateOfItems(navigationSuiteItems)
                val footerScope by rememberStateOfItems(footerItems)
                val secondaryScope by rememberStateOfItems(secondaryItems)
                ModalNavigationDrawer(
                    drawerContent = {
                        ModalDrawerSheet(
                            drawerContainerColor = navigationSuiteColors.navigationDrawerContainerColor,
                            drawerContentColor = navigationSuiteColors.navigationDrawerContentColor,
                        ) {
                            DrawerContent(
                                drawerHeader = drawerHeader,
                                showPrimaryItems = layoutType != NavigationSuiteType.NavigationBar,
                                scope = scope,
                                secondaryScope = secondaryScope,
                                footerScope = footerScope,
                                onItemClicked = {
                                    coroutineScope.launch {
                                        drawerState.close()
                                    }
                                },
                            )
                        }
                    },
                    gesturesEnabled = layoutType != NavigationSuiteType.NavigationDrawer && drawerGesturesEnabled,
                    drawerState = drawerState,
                ) {
                    Row {
                        AnimatedVisibility(layoutType == NavigationSuiteType.NavigationRail) {
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
                                if (!LocalSpatialCapabilities.current.isSpatialUiEnabled) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
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
                        }
                        AnimatedVisibility(layoutType == NavigationSuiteType.NavigationDrawer) {
                            PermanentDrawerSheet(
                                modifier =
                                    Modifier
                                        .width(240.dp)
                                        .padding(horizontal = 12.dp),
                                drawerContainerColor = navigationSuiteColors.navigationDrawerContainerColor,
                                drawerContentColor = navigationSuiteColors.navigationDrawerContentColor,
                            ) {
                                DrawerContent(
                                    drawerHeader = drawerHeader,
                                    showPrimaryItems = true,
                                    scope = scope,
                                    secondaryScope = secondaryScope,
                                    footerScope = footerScope,
                                    onItemClicked = {},
                                )
                            }
                        }
                        Box {
                            CompositionLocalProvider(
                                LocalBottomBarHeight provides
                                    if (layoutType == NavigationSuiteType.NavigationBar) {
                                        bottomBarHeight
                                    } else {
                                        0.dp
                                    },
                            ) {
                                content.invoke()
                            }
                            androidx.compose.animation.AnimatedVisibility(
                                layoutType == NavigationSuiteType.NavigationBar,
                                enter = slideInVertically { it },
                                exit = slideOutVertically { it },
                                modifier =
                                    Modifier
                                        .align(Alignment.BottomCenter)
                                        .offset { IntOffset(x = 0, y = -bottomBarOffsetHeightPx.roundToInt()) },
                            ) {
                                Surface(
                                    //                        color = navigationSuiteColors.navigationBarContainerColor,
                                    contentColor = navigationSuiteColors.navigationBarContentColor,
                                ) {
                                    Box {
                                        if (bottomBarDividerEnabled) {
                                            HorizontalDivider(
                                                modifier =
                                                    Modifier
                                                        .align(Alignment.TopCenter)
                                                        .fillMaxWidth(),
                                                color = FlareDividerDefaults.color,
                                                thickness = FlareDividerDefaults.thickness,
                                            )
                                        }
                                        Row(
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .windowInsetsPadding(
                                                        WindowInsets.systemBars.only(
                                                            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                                                        ),
                                                    ),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            scope.itemList.forEach {
                                                Box(
                                                    modifier =
                                                        it.modifier
                                                            .weight(1f)
                                                            .combinedClickable(
                                                                interactionSource = it.interactionSource,
                                                                onClick = it.onClick,
                                                                indication = LocalIndication.current,
                                                                onLongClick = it.onLongClick,
                                                            ).height(bottomBarHeight),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    val colors =
                                                        it.colors?.navigationBarItemColors ?: NavigationBarItemDefaults.colors()
                                                    val color =
                                                        with(colors) {
                                                            when {
                                                                !it.enabled -> disabledIconColor
                                                                it.selected -> MaterialTheme.colorScheme.primary
                                                                else -> unselectedIconColor
                                                            }
                                                        }
                                                    val iconColor by animateColorAsState(
                                                        targetValue = color,
                                                        animationSpec = tween(100),
                                                    )
                                                    CompositionLocalProvider(LocalContentColor provides iconColor) {
                                                        NavigationItemIcon(icon = it.icon, badge = it.badge)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionalSubspace(content: @Composable () -> Unit) {
    val session = LocalSession.current
    if (LocalSpatialCapabilities.current.isSpatialUiEnabled) {
        androidx.xr.compose.spatial.Subspace {
            SpatialPanel(
                SubspaceModifier
                    .width(1280.dp)
                    .height(800.dp)
                    .resizable()
                    .movable(),
            ) {
                content.invoke()

                Orbiter(
                    position = OrbiterEdge.Top,
                    offset = EdgeOffset.inner(offset = 20.dp),
                    alignment = Alignment.End,
                    shape = SpatialRoundedCornerShape(CornerSize(28.dp)),
                ) {
                    FilledTonalIconButton(
                        onClick = {
                            session?.requestHomeSpaceMode()
                        },
                        modifier = Modifier.size(56.dp),
                    ) {
                        FAIcon(
                            FontAwesomeIcons.Solid.DownLeftAndUpRightToCenter,
                            contentDescription = null,
                        )
                    }
                }
            }
        }
    } else {
        content.invoke()
    }
}

@Composable
private fun ColumnScope.DrawerContent(
    drawerHeader: @Composable (ColumnScope.() -> Unit)?,
    onItemClicked: () -> Unit,
    showPrimaryItems: Boolean,
    scope: NavigationSuiteItemProvider,
    secondaryScope: NavigationSuiteItemProvider,
    footerScope: NavigationSuiteItemProvider,
) {
    drawerHeader?.invoke(this)
    if (showPrimaryItems) {
        scope.itemList.forEach {
            NavigationDrawerItem(
                modifier = it.modifier,
                selected = it.selected,
                onClick = {
                    it.onClick()
                    onItemClicked()
                },
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
    if (secondaryScope.itemList.isNotEmpty()) {
        HorizontalDivider()
    }
    secondaryScope.itemList.forEach {
        NavigationDrawerItem(
            modifier = it.modifier,
            selected = it.selected,
            onClick = {
                it.onClick()
                onItemClicked()
            },
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
            onClick = {
                it.onClick()
                onItemClicked()
            },
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
    val onLongClick: (() -> Unit)? = null,
)

/** The scope associated with the [NavigationSuiteScope]. */
@ExperimentalMaterial3AdaptiveNavigationSuiteApi
interface NavigationSuiteScope2 {
    /**
     * This function sets the parameters of the default Material navigation item to be used with the
     * Navigation Suite Scaffold. The item is called in [NavigationSuite], according to the
     * current [NavigationSuiteType].
     *
     * For specifics about each item component, see [NavigationBarItem], [NavigationRailItem], and
     * [NavigationDrawerItem].
     *
     * @param selected whether this item is selected
     * @param onClick called when this item is clicked
     * @param icon icon for this item, typically an [Icon]
     * @param modifier the [Modifier] to be applied to this item
     * @param enabled controls the enabled state of this item. When `false`, this component will not
     * respond to user input, and it will appear visually disabled and disabled to accessibility
     * services. Note: as of now, for [NavigationDrawerItem], this is always `true`.
     * @param label the text label for this item
     * @param alwaysShowLabel whether to always show the label for this item. If `false`, the label will
     * only be shown when this item is selected. Note: for [NavigationDrawerItem] this is always `true`
     * @param badge optional badge to show on this item
     * @param colors [NavigationSuiteItemColors] that will be used to resolve the colors used for this
     * item in different states.
     * @param interactionSource an optional hoisted [MutableInteractionSource] for observing and
     * emitting [Interaction]s for this item. You can use this to change the item's appearance
     * or preview the item in different states. Note that if `null` is provided, interactions will
     * still happen internally.
     */
    fun item(
        selected: Boolean,
        onClick: () -> Unit,
        icon: @Composable () -> Unit,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        label: @Composable (() -> Unit)? = null,
        alwaysShowLabel: Boolean = true,
        badge: (@Composable () -> Unit)? = null,
        colors: NavigationSuiteItemColors? = null,
        interactionSource: MutableInteractionSource? = null,
        onLongClick: (() -> Unit)? = null,
    )
}

@OptIn(ExperimentalMaterial3AdaptiveNavigationSuiteApi::class)
private class NavigationSuiteScopeImpl :
    NavigationSuiteScope2,
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
        onLongClick: (() -> Unit)?,
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
                onLongClick = onLongClick,
            ),
        )
    }

    override val itemList: MutableVector<NavigationSuiteItem> = mutableVectorOf()

    override val itemsCount: Int
        get() = itemList.size
}

@OptIn(ExperimentalMaterial3AdaptiveNavigationSuiteApi::class)
@Composable
private fun rememberStateOfItems(content: NavigationSuiteScope2.() -> Unit): State<NavigationSuiteItemProvider> {
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
    val iconContent = remember(icon) { movableContentOf(icon) }
    if (badge != null) {
        BadgedBox(badge = { badge.invoke() }) {
            iconContent.invoke()
        }
    } else {
        iconContent.invoke()
    }
}
