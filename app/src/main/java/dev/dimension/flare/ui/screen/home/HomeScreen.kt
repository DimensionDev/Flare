package dev.dimension.flare.ui.screen.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.ExperimentalMaterial3AdaptiveNavigationSuiteApi
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entry
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSavedStateNavEntryDecorator
import androidx.navigation3.ui.DialogSceneStrategy
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.ui.rememberSceneSetupNavEntryDecorator
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.EllipsisVertical
import compose.icons.fontawesomeicons.solid.Gear
import compose.icons.fontawesomeicons.solid.Pen
import dev.dimension.flare.R
import dev.dimension.flare.data.model.AllListTabItem
import dev.dimension.flare.data.model.Bluesky
import dev.dimension.flare.data.model.DirectMessageTabItem
import dev.dimension.flare.data.model.DiscoverTabItem
import dev.dimension.flare.data.model.HomeTimelineTabItem
import dev.dimension.flare.data.model.NotificationTabItem
import dev.dimension.flare.data.model.ProfileTabItem
import dev.dimension.flare.data.model.RssTabItem
import dev.dimension.flare.data.model.SettingsTabItem
import dev.dimension.flare.data.model.TabItem
import dev.dimension.flare.data.model.TimelineTabItem
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.InAppNotificationComponent
import dev.dimension.flare.ui.component.NavigationSuiteScaffold2
import dev.dimension.flare.ui.component.RichText
import dev.dimension.flare.ui.model.isSuccess
import dev.dimension.flare.ui.model.onLoading
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.HomeTabsPresenter
import dev.dimension.flare.ui.presenter.home.ActiveAccountPresenter
import dev.dimension.flare.ui.presenter.home.UserPresenter
import dev.dimension.flare.ui.presenter.home.UserState
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.settings.AccountsPresenter
import dev.dimension.flare.ui.route.Route
import dev.dimension.flare.ui.route.Router
import dev.dimension.flare.ui.screen.settings.AccountItem
import dev.dimension.flare.ui.screen.settings.TabIcon
import dev.dimension.flare.ui.screen.settings.TabTitle
import dev.dimension.flare.ui.screen.splash.SplashScreen
import dev.dimension.flare.ui.theme.FlareTheme
import dev.dimension.flare.ui.theme.MediumAlpha
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter
import org.koin.compose.koinInject
import soup.compose.material.motion.animation.materialElevationScaleIn
import soup.compose.material.motion.animation.materialElevationScaleOut
import soup.compose.material.motion.animation.materialFadeIn
import soup.compose.material.motion.animation.materialFadeOut
import soup.compose.material.motion.animation.materialSharedAxisXIn
import soup.compose.material.motion.animation.materialSharedAxisXOut
import soup.compose.material.motion.animation.rememberSlideDistance

@OptIn(
    ExperimentalMaterial3AdaptiveNavigationSuiteApi::class,
    ExperimentalMaterial3Api::class,
)
@Composable
internal fun HomeScreen(
    afterInit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val state by producePresenter { presenter() }
    val hapticFeedback = LocalHapticFeedback.current
    state.tabs
        .onSuccess { tabs ->
            val backStack = rememberNavBackStack(getDirection(tabs.all.first().tabItem))

            fun navigate(route: Route) {
                if (backStack.contains(route)) {
                    backStack.remove(route)
                }
                backStack.add(route)
            }

            fun onBack() {
                backStack.removeAt(backStack.lastIndex)
            }

            val currentRoute by remember {
                derivedStateOf {
                    backStack.last()
                }
            }
            LaunchedEffect(Unit) {
                afterInit.invoke()
            }
            val currentTab by remember {
                derivedStateOf {
                    tabs.all.firstOrNull { getDirection(it.tabItem) == currentRoute }?.tabItem
                }
            }

            val accountTypeState by producePresenter(key = "home_account_type_${currentTab?.account}") {
                accountTypePresenter(currentTab?.account ?: AccountType.Active)
            }
            val drawerState = rememberDrawerState(DrawerValue.Closed)
            val layoutType =
                NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(
                    currentWindowAdaptiveInfo(),
                )
            val actualLayoutType = state.navigationState.type ?: layoutType
            FlareTheme {
                Box(
                    modifier = modifier,
                ) {
                    NavigationSuiteScaffold2(
                        modifier = Modifier.fillMaxSize(),
                        bottomBarDividerEnabled = state.navigationState.bottomBarDividerEnabled,
                        bottomBarAutoHideEnabled = state.navigationState.bottomBarAutoHideEnabled,
                        layoutType = actualLayoutType,
                        drawerHeader = {
                            DrawerHeader(
                                accountTypeState,
                                currentTab,
                                toAccoutSwitcher = {
                                    state.setShowAccountSelection(true)
                                },
                                showFab = actualLayoutType == NavigationSuiteType.NavigationDrawer,
                                toCompose = {
                                    navigate(
                                        Route.Compose.New(it),
                                    )
                                },
                                toProfile = {
                                    navigate(
                                        Route.Profile.Me(
                                            accountType = AccountType.Specific(it),
                                        ),
                                    )
                                },
                            )
                        },
                        railHeader = {
                            accountTypeState.user.onSuccess {
                                Spacer(modifier = Modifier.height(4.dp))
                                AvatarComponent(
                                    it.avatar,
                                    size = 56.dp,
                                    modifier =
                                        Modifier.clickable {
                                            scope.launch {
                                                drawerState.open()
                                            }
                                        },
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                FloatingActionButton(
                                    onClick = {
                                        currentTab?.let {
                                            navigate(
                                                Route.Compose.New(it.account),
                                            )
                                        }
                                    },
                                    elevation =
                                        FloatingActionButtonDefaults.elevation(
                                            defaultElevation = 0.dp,
                                        ),
                                ) {
                                    FAIcon(
                                        imageVector = FontAwesomeIcons.Solid.Pen,
                                        contentDescription = stringResource(id = R.string.compose_title),
                                    )
                                }
                            }
                        },
                        navigationSuiteItems = {
                            tabs.primary.forEach { (tab, tabState, badgeState) ->
                                item(
                                    selected = currentRoute == getDirection(tab),
                                    onClick = {
                                        if (currentRoute == getDirection(tab)) {
                                            tabState.onClick()
                                        } else {
                                            navigate(getDirection(tab))
                                        }
                                    },
                                    icon = {
                                        TabIcon(
                                            accountType = tab.account,
                                            icon = tab.metaData.icon,
                                            title = tab.metaData.title,
                                        )
                                    },
                                    label = {
                                        TabTitle(
                                            title = tab.metaData.title,
                                        )
                                    },
                                    badge =
                                        if (badgeState.isSuccess) {
                                            {
                                                badgeState.onSuccess {
                                                    if (it > 0) {
                                                        Badge {
                                                            Text(text = it.toString())
                                                        }
                                                    }
                                                }
                                            }
                                        } else {
                                            null
                                        },
                                    onLongClick =
                                        if (tab is HomeTimelineTabItem || tab is ProfileTabItem) {
                                            {
                                                hapticFeedback.performHapticFeedback(
                                                    HapticFeedbackType.LongPress,
                                                )
                                                state.setShowAccountSelection(true)
                                            }
                                        } else {
                                            null
                                        },
                                )
                            }
                        },
                        secondaryItems = {
                            tabs.secondary.forEach { (tab, tabState) ->
                                item(
                                    selected = currentRoute == getDirection(tab),
                                    onClick = {
                                        if (currentRoute == getDirection(tab)) {
                                            tabState.onClick()
                                        } else {
                                            navigate(getDirection(tab))
                                        }
                                    },
                                    icon = {
                                        TabIcon(
                                            accountType = tab.account,
                                            icon = tab.metaData.icon,
                                            title = tab.metaData.title,
                                            iconOnly = tabs.secondaryIconOnly,
                                        )
                                    },
                                    label = {
                                        TabTitle(
                                            title = tab.metaData.title,
                                        )
                                    },
                                )
                            }
                        },
                        footerItems = {
                            accountTypeState.user.onSuccess {
                                item(
                                    selected = currentRoute is Route.Settings.Main,
                                    onClick = {
                                        navigate(Route.Settings.Main)
                                    },
                                    icon = {
                                        FAIcon(
                                            imageVector = FontAwesomeIcons.Solid.Gear,
                                            contentDescription = stringResource(id = R.string.settings_title),
                                        )
                                    },
                                    label = {
                                        Text(text = stringResource(id = R.string.settings_title))
                                    },
                                )
                            }
                        },
                        drawerGesturesEnabled =
                            state.navigationState.drawerEnabled &&
                                accountTypeState.user.isSuccess,
                        drawerState = drawerState,
                    ) {
                        val slideDistance = rememberSlideDistance()
                        with(drawerState) {
                            with(state.navigationState) {
                                NavDisplay(
                                    sceneStrategy = remember { DialogSceneStrategy() },
                                    backStack = backStack,
                                    entryDecorators =
                                        listOf(
                                            rememberSceneSetupNavEntryDecorator(),
                                            rememberSavedStateNavEntryDecorator(),
                                            rememberViewModelStoreNavEntryDecorator(),
                                        ),
                                    transitionSpec = {
                                        materialSharedAxisXIn(true, slideDistance) togetherWith
                                            materialSharedAxisXOut(true, slideDistance)
                                    },
                                    popTransitionSpec = {
                                        materialSharedAxisXIn(false, slideDistance) togetherWith
                                            materialSharedAxisXOut(false, slideDistance)
                                    },
                                    predictivePopTransitionSpec = {
                                        materialSharedAxisXIn(
                                            false,
                                            slideDistance,
                                        ) + materialElevationScaleIn() + materialFadeIn() togetherWith
                                            materialSharedAxisXOut(
                                                false,
                                                slideDistance,
                                            ) + materialElevationScaleOut() + materialFadeOut()
                                    },
                                    entryProvider =
                                        entryProvider {
                                            tabs.all.forEach { (tab, tabState) ->
                                                entry(
                                                    getDirection(tab),
                                                ) {
                                                    CompositionLocalProvider(
                                                        LocalTabState provides tabState,
                                                    ) {
                                                        Router(getDirection(tab), state.navigationState, drawerState)
                                                    }
                                                }
                                            }
                                            entry<Route.Settings.Main> {
                                                Router(Route.Settings.Main, state.navigationState, drawerState)
                                            }
                                            entry<Route.ServiceSelect.Selection> {
                                                Router(Route.ServiceSelect.Selection, state.navigationState, drawerState)
                                            }
                                            entry<Route.Compose.New>(
                                                metadata = DialogSceneStrategy.dialog(),
                                            ) { args ->
                                                Router(
                                                    Route.Compose.New(args.accountType),
                                                    state.navigationState,
                                                    drawerState,
                                                )
                                            }
                                        },
                                )
                            }
                        }
                    }
                    BackHandler(
                        enabled = drawerState.isOpen,
                        onBack = {
                            scope.launch {
                                drawerState.close()
                            }
                        },
                    )
                    InAppNotificationComponent(
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                }

                if (state.showAccountSelection) {
                    ModalBottomSheet(
                        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                        onDismissRequest = {
                            state.setShowAccountSelection(false)
                        },
                    ) {
                        state.accountSelectionState.accounts.onSuccess {
                            for (index in 0 until it.size) {
                                val (accountKey, data) = it[index]
                                AccountItem(
                                    userState = data,
                                    onClick = {
                                        state.accountSelectionState.setActiveAccount(it)
                                        state.setShowAccountSelection(false)
                                    },
                                    toLogin = {
                                        navigate(Route.ServiceSelect.Selection)
                                    },
                                    trailingContent = { user ->
                                        state.accountSelectionState.activeAccount.onSuccess {
                                            RadioButton(
                                                selected = it.accountKey == user.key,
                                                onClick = {
                                                    state.accountSelectionState.setActiveAccount(user.key)
                                                    state.setShowAccountSelection(false)
                                                },
                                            )
                                        }
                                    },
                                )
                            }
                        }
                        Button(
                            onClick = {
                                state.setShowAccountSelection(false)
                                scope.launch {
                                    drawerState.close()
                                }
                                navigate(Route.ServiceSelect.Selection)
                            },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal = screenHorizontalPadding,
                                        vertical = 16.dp,
                                    ),
                        ) {
                            Text(text = stringResource(R.string.quick_menu_add_account))
                        }
                    }
                }
            }
        }.onLoading {
            SplashScreen()
        }
}

@Composable
private fun ColumnScope.DrawerHeader(
    accountTypeState: UserState,
    currentTab: TabItem?,
    toCompose: (accountType: AccountType) -> Unit,
    toProfile: (userKey: MicroBlogKey) -> Unit,
    toAccoutSwitcher: () -> Unit,
    showFab: Boolean = true,
) {
    accountTypeState.user.onSuccess { data ->
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        toProfile.invoke(data.key)
                    },
        ) {
            Column(
                modifier =
                    Modifier
                        .padding(
                            vertical = 16.dp,
                            horizontal = screenHorizontalPadding,
                        ),
            ) {
                AvatarComponent(
                    data = data.avatar,
                    size = 64.dp,
                )
                Spacer(modifier = Modifier.height(4.dp))
                RichText(
                    text = data.name,
                    textStyle = MaterialTheme.typography.titleMedium,
                )
                Text(
                    data.handle,
                    style = MaterialTheme.typography.bodySmall,
                    modifier =
                        Modifier
                            .alpha(MediumAlpha),
                )
            }
            IconButton(
                onClick = {
                    toAccoutSwitcher.invoke()
                },
                modifier =
                    Modifier
                        .padding(
                            horizontal = 8.dp,
                        ),
            ) {
                FAIcon(
                    FontAwesomeIcons.Solid.EllipsisVertical,
                    contentDescription = null,
                )
            }
        }
    }
    if (showFab) {
        accountTypeState.user.onSuccess {
            ExtendedFloatingActionButton(
                onClick = {
                    currentTab?.let {
                        toCompose.invoke(it.account)
                    }
                },
                icon = {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.Pen,
                        contentDescription = stringResource(id = R.string.compose_title),
                    )
                },
                text = {
                    Text(text = stringResource(id = R.string.compose_title))
                },
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

private fun getDirection(
    tab: TabItem,
    accountType: AccountType = tab.account,
): Route =
    when (tab) {
        is DiscoverTabItem -> {
            Route.Discover(accountType)
        }

        is ProfileTabItem -> {
            Route.Profile.Me(accountType)
        }

        is HomeTimelineTabItem -> {
            Route.Home(accountType)
        }

        is TimelineTabItem -> {
            Route.Timeline(accountType, tab)
        }

        is NotificationTabItem -> {
            Route.Notification(accountType)
        }

        SettingsTabItem -> {
            Route.Settings.Main
        }

        is AllListTabItem -> Route.Lists.List(accountType)
        is Bluesky.FeedsTabItem -> Route.Bluesky.Feed(accountType)
        is DirectMessageTabItem -> Route.DM.List(accountType)
        is RssTabItem -> Route.Rss.Sources
    }

@Composable
private fun presenter(settingsRepository: SettingsRepository = koinInject()) =
    run {
        val account =
            remember {
                ActiveAccountPresenter()
            }.invoke()
        val navigationState =
            remember {
                NavigationState()
            }
        val tabs =
            remember {
                HomeTabsPresenter(settingsRepository.tabSettings)
            }.invoke()
        var showAccountSelection by remember {
            mutableStateOf(false)
        }
        val accountSelectionState =
            remember {
                AccountsPresenter()
            }.invoke()
        object {
            val tabs = tabs.tabs
            val navigationState = navigationState
            val showAccountSelection = showAccountSelection
            val accountSelectionState = accountSelectionState

            fun setShowAccountSelection(value: Boolean) {
                showAccountSelection = value
            }
        }
    }

@Composable
private fun accountTypePresenter(accountType: AccountType) =
    run {
        remember(accountType) { UserPresenter(accountType, null) }.invoke()
    }

internal class NavigationState {
    private val state = mutableStateOf<NavigationSuiteType?>(null)
    private val drawerState = mutableStateOf(true)
    private val bottomBarAutoHideState = mutableStateOf(true)
    private val bottomBarDividerState = mutableStateOf(true)
    val type: NavigationSuiteType?
        get() = state.value

    val drawerEnabled: Boolean
        get() = drawerState.value
    val bottomBarAutoHideEnabled: Boolean
        get() = bottomBarAutoHideState.value
    val bottomBarDividerEnabled: Boolean
        get() = bottomBarDividerState.value

    fun hide() {
        state.value = NavigationSuiteType.None
    }

    fun show() {
        state.value = null
    }

    fun enableDrawer() {
        drawerState.value = true
    }

    fun disableDrawer() {
        drawerState.value = false
    }

    fun enableBottomBarAutoHide() {
        bottomBarAutoHideState.value = true
    }

    fun disableBottomBarAutoHide() {
        bottomBarAutoHideState.value = false
    }

    fun showBottomBarDivider() {
        bottomBarDividerState.value = true
    }

    fun hideBottomBarDivider() {
        bottomBarDividerState.value = false
    }
}

private val LocalTabState =
    androidx.compose.runtime.staticCompositionLocalOf<HomeTabsPresenter.State.HomeTabState.HomeTabItem.TabState?> {
        null
    }

@Composable
internal fun RegisterTabCallback(lazyListState: LazyStaggeredGridState) {
    val tabState = LocalTabState.current
    if (tabState != null) {
        val scope = rememberCoroutineScope()
        val callback: () -> Unit =
            remember(lazyListState, scope) {
                {
                    scope.launch {
                        if (lazyListState.firstVisibleItemIndex > 20) {
                            lazyListState.scrollToItem(0)
                        } else {
                            lazyListState.animateScrollToItem(0)
                        }
                    }
                }
            }
        DisposableEffect(tabState, callback, lazyListState) {
            tabState.registerCallback(callback)
            onDispose {
                tabState.unregisterCallback(callback)
            }
        }
    }
}
