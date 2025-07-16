package dev.dimension.flare.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults.floatingToolbarVerticalNestedScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalWideNavigationRail
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.WideNavigationRailState
import androidx.compose.material3.WideNavigationRailValue
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.collection.MutableVector
import androidx.compose.runtime.collection.mutableVectorOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Pen
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.rememberHazeState
import dev.dimension.flare.R

val LocalBottomBarHeight = androidx.compose.runtime.staticCompositionLocalOf<Dp> { 0.dp }
val LocalBottomBarShowing = androidx.compose.runtime.staticCompositionLocalOf<Boolean> { false }

@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalSharedTransitionApi::class,
    ExperimentalHazeMaterialsApi::class,
)
@ExperimentalMaterial3AdaptiveNavigationSuiteApi
@Composable
fun NavigationSuiteScaffold2(
    navigationSuiteItems: NavigationSuiteScope2.() -> Unit,
    secondaryItems: NavigationSuiteScope2.() -> Unit,
    wideNavigationRailState: WideNavigationRailState,
    modifier: Modifier = Modifier,
    bottomBarAutoHideEnabled: Boolean = true,
    showFab: Boolean = true,
    onFabClicked: () -> Unit = {},
    layoutType: NavigationSuiteType =
        NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfo()),
    navigationSuiteColors: NavigationSuiteColors = NavigationSuiteDefaults.colors(),
    containerColor: Color = MaterialTheme.colorScheme.background,
    contentColor: Color = contentColorFor(containerColor),
    railHeader: @Composable (ColumnScope.() -> Unit)? = null,
    footerItems: NavigationSuiteScope2.() -> Unit = {},
    content: @Composable () -> Unit = {},
) {
    val hazeState = rememberHazeState()
    var isPodcastShowing by remember { mutableStateOf(false) }
    var isBottomBarExpanded by remember { mutableStateOf(true) }
    Surface(
        modifier =
            modifier
                .let {
                    if (bottomBarAutoHideEnabled) {
                        it.floatingToolbarVerticalNestedScroll(
                            expanded = isBottomBarExpanded,
                            onExpand = { isBottomBarExpanded = true },
                            onCollapse = { isBottomBarExpanded = false },
                        )
                    } else {
                        it
                    }
                },
        color = containerColor,
        contentColor = contentColor,
    ) {
        val scope by rememberStateOfItems(navigationSuiteItems)
        val footerScope by rememberStateOfItems(footerItems)
        val secondaryScope by rememberStateOfItems(secondaryItems)

        Row {
            ModalWideNavigationRail(
                hideOnCollapse = layoutType == NavigationSuiteType.NavigationBar,
                state = wideNavigationRailState,
                colors =
                    navigationSuiteColors.wideNavigationRailColors
                        .copy(
                            containerColor = Color.Transparent,
                        ),
            ) {
                val actualLayoutType =
                    if (layoutType == NavigationSuiteType.NavigationBar) {
                        NavigationSuiteType.NavigationDrawer
                    } else if (layoutType == NavigationSuiteType.NavigationRail) {
                        if (wideNavigationRailState.currentValue == WideNavigationRailValue.Expanded) {
                            NavigationSuiteType.WideNavigationRailExpanded
                        } else {
                            NavigationSuiteType.WideNavigationRailCollapsed
                        }
                    } else {
                        layoutType
                    }
                Column(
                    modifier =
                        Modifier
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                    verticalArrangement =
                        Arrangement.spacedBy(
                            if (layoutType == NavigationSuiteType.NavigationBar) {
                                0.dp
                            } else {
                                8.dp
                            },
                        ),
                ) {
                    railHeader?.invoke(this)
                    if (layoutType != NavigationSuiteType.NavigationBar) {
                        scope.itemList.forEach {
                            androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem(
                                modifier = it.modifier,
                                selected = it.selected,
                                onClick = it.onClick,
                                icon = it.icon,
                                enabled = it.enabled,
                                label = it.label,
                                interactionSource = it.interactionSource,
                                navigationSuiteType = actualLayoutType,
                                badge = it.badge,
                            )
                        }
                    }
                    if (wideNavigationRailState.currentValue == WideNavigationRailValue.Expanded) {
                        if (secondaryScope.itemList.isNotEmpty()) {
                            HorizontalDivider()
                        }
                        secondaryScope.itemList.forEach {
                            androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem(
                                modifier = it.modifier,
                                selected = it.selected,
                                onClick = it.onClick,
                                icon = it.icon,
                                enabled = it.enabled,
                                label = it.label,
                                interactionSource = it.interactionSource,
                                navigationSuiteType = actualLayoutType,
                                badge = it.badge,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    footerScope.itemList.forEach {
                        androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem(
                            modifier = it.modifier,
                            selected = it.selected,
                            onClick = it.onClick,
                            icon = it.icon,
                            enabled = it.enabled,
                            label = it.label,
                            interactionSource = it.interactionSource,
                            navigationSuiteType = actualLayoutType,
                            badge = it.badge,
                        )
                    }
                }
            }
            Box {
                CompositionLocalProvider(
                    LocalBottomBarHeight provides
                        if (isPodcastShowing) {
                            56.dp
                        } else {
                            0.dp
                        } +
                        if (scope.itemList.any { item -> item.selected } && layoutType == NavigationSuiteType.NavigationBar) {
                            56.dp
                        } else {
                            0.dp
                        },
                    LocalBottomBarShowing provides (layoutType == NavigationSuiteType.NavigationBar),
                    LocalHazeState provides hazeState,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .hazeSource(state = hazeState),
                    ) {
                        content.invoke()
                    }
                }
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .animateContentSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    PodcastFAB(
                        onVisibilityChanged = {
                            isPodcastShowing = it
                        },
                        modifier =
                            Modifier
                                .let {
                                    if (layoutType == NavigationSuiteType.NavigationBar) {
                                        it
                                    } else {
                                        it.windowInsetsPadding(
                                            WindowInsets.systemBars.only(
                                                WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                                            ),
                                        )
                                    }
                                },
                    )
                    androidx.compose.animation.AnimatedVisibility(
                        layoutType == NavigationSuiteType.NavigationBar &&
                            scope.itemList.any { item -> item.selected },
                        enter = slideInVertically { it },
                        exit = slideOutVertically { it },
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .windowInsetsPadding(
                                        WindowInsets.systemBars.only(
                                            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                                        ),
                                    ).padding(
                                        horizontal = 16.dp,
                                        vertical = 8.dp,
                                    ),
                        ) {
                            SharedTransitionLayout {
                                AnimatedContent(
                                    isBottomBarExpanded,
                                ) { isExpanded ->
                                    Glassify(
                                        shape = RoundedCornerShape(50),
                                        shadowElevation = 8.dp,
                                        tonalElevation = 8.dp,
                                        hazeState = hazeState,
                                        hazeStyle = HazeMaterials.thick(),
                                        modifier =
                                            Modifier
                                                .sharedElement(
                                                    rememberSharedContentState("bottom_surface"),
                                                    animatedVisibilityScope = this@AnimatedContent,
                                                ),
                                    ) {
                                        Row(
                                            modifier =
                                                Modifier
                                                    .padding(
                                                        horizontal = 12.dp,
                                                        vertical = 4.dp,
                                                    ),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            scope.itemList.forEachIndexed { index, it ->
                                                if (isExpanded || it.selected) {
                                                    ShortNavigationBarItem(
                                                        modifier =
                                                            it.modifier
                                                                .sharedElement(
                                                                    rememberSharedContentState("item_$index"),
                                                                    animatedVisibilityScope = this@AnimatedContent,
                                                                ),
                                                        selected = it.selected,
                                                        onClick = it.onClick,
                                                        icon = {
                                                            Box(
                                                                modifier =
                                                                    Modifier
                                                                        .sharedElement(
                                                                            rememberSharedContentState(
                                                                                "item_icon_$index",
                                                                            ),
                                                                            animatedVisibilityScope = this@AnimatedContent,
                                                                        ),
                                                            ) {
                                                                NavigationItemIcon(
                                                                    badge = it.badge,
                                                                    icon = it.icon,
                                                                )
                                                            }
                                                        },
                                                        enabled = it.enabled,
                                                        label = if (isExpanded) it.label else null,
                                                        interactionSource = it.interactionSource,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            if (showFab) {
                                SharedTransitionLayout {
                                    AnimatedContent(
                                        isBottomBarExpanded,
                                    ) { isExpanded ->
                                        Glassify(
                                            onClick = onFabClicked,
                                            shape = CircleShape,
                                            shadowElevation = 8.dp,
                                            tonalElevation = 8.dp,
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            hazeState = hazeState,
                                            modifier =
                                                Modifier
                                                    .sharedElement(
                                                        rememberSharedContentState("compose_fab"),
                                                        animatedVisibilityScope = this@AnimatedContent,
                                                    ).size(
                                                        if (isExpanded) {
                                                            56.dp
                                                        } else {
                                                            40.dp
                                                        },
                                                    ),
                                        ) {
                                            Icon(
                                                imageVector = FontAwesomeIcons.Solid.Pen,
                                                contentDescription = stringResource(id = R.string.compose_title),
                                                modifier = Modifier.size(20.dp),
                                            )
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
