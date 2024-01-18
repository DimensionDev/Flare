package dev.dimension.flare.ui.screen.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.AnimatedPane
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.PaneScaffoldDirective
import androidx.compose.material3.adaptive.ThreePaneScaffoldNavigator
import androidx.compose.material3.adaptive.calculateStandardPaneScaffoldDirective
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.navigation.dependency
import dev.dimension.flare.R
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.component.ThemeWrapper
import dev.dimension.flare.ui.presenter.home.ActiveAccountPresenter
import dev.dimension.flare.ui.presenter.home.ActiveAccountState
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.screen.NavGraphs
import dev.dimension.flare.ui.screen.destinations.AboutRouteDestination
import dev.dimension.flare.ui.screen.destinations.AccountsRouteDestination
import dev.dimension.flare.ui.screen.destinations.AppearanceRouteDestination
import dev.dimension.flare.ui.screen.destinations.DirectionDestination
import dev.dimension.flare.ui.screen.destinations.StorageRouteDestination
import dev.dimension.flare.ui.screen.home.Router

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Destination(
    wrappers = [ThemeWrapper::class],
)
@Composable
internal fun SettingsRoute() {
    val settingsPanelState by producePresenter {
        settingsPanelPresenter()
    }
    val scaffoldNavigator =
        rememberListDetailPaneScaffoldNavigator(
            scaffoldDirective =
                calculateStandardPaneScaffoldDirective(currentWindowAdaptiveInfo()).let {
                    PaneScaffoldDirective(
                        contentPadding = PaddingValues(0.dp),
                        maxHorizontalPartitions = it.maxHorizontalPartitions,
                        horizontalPartitionSpacerSize = it.horizontalPartitionSpacerSize,
                        maxVerticalPartitions = it.maxVerticalPartitions,
                        verticalPartitionSpacerSize = it.verticalPartitionSpacerSize,
                        excludedBounds = it.excludedBounds,
                    )
                },
        )
    LaunchedEffect(settingsPanelState.selectedItem) {
        if (settingsPanelState.selectedItem != null) {
            scaffoldNavigator.navigateTo(ListDetailPaneScaffoldRole.Detail)
        } else if (scaffoldNavigator.canNavigateBack()) {
            scaffoldNavigator.navigateBack()
        }
    }
    BackHandler(
        scaffoldNavigator.canNavigateBack(),
    ) {
        settingsPanelState.setSelectedItem(null)
    }
    ListDetailPaneScaffold(
        scaffoldState = scaffoldNavigator.scaffoldState,
        listPane = {
            AnimatedPane(modifier = Modifier) {
                SettingsScreen(
                    toAccounts = {
                        settingsPanelState.setSelectedItem(AccountsRouteDestination)
                    },
                    toAppearance = {
                        settingsPanelState.setSelectedItem(AppearanceRouteDestination)
                    },
                    toStorage = {
                        settingsPanelState.setSelectedItem(StorageRouteDestination)
                    },
                    toAbout = {
                        settingsPanelState.setSelectedItem(AboutRouteDestination)
                    },
                )
            }
        },
        detailPane = {
            AnimatedPane(modifier = Modifier) {
                settingsPanelState.selectedItem?.let { item ->
                    Router(navGraph = NavGraphs.root, item) {
                        dependency(
                            ProxyDestinationsNavigator(
                                scaffoldNavigator,
                                destinationsNavigator,
                                navigateBack = {
                                    settingsPanelState.setSelectedItem(null)
                                },
                            ),
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun settingsPanelPresenter() =
    run {
        var selectedItem by remember { mutableStateOf<DirectionDestination?>(null) }
        object {
            val selectedItem = selectedItem

            fun setSelectedItem(item: DirectionDestination?) {
                selectedItem = item
            }
        }
    }

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
internal class ProxyDestinationsNavigator(
    private val scaffoldNavigator: ThreePaneScaffoldNavigator,
    private val navigator: DestinationsNavigator,
    private val navigateBack: () -> Unit,
) : DestinationsNavigator by navigator {
    override fun navigateUp(): Boolean {
        return if (navigator.navigateUp()) {
            true
        } else if (scaffoldNavigator.canNavigateBack()) {
            navigateBack()
            true
        } else {
            false
        }
    }

    override fun popBackStack(): Boolean {
        return if (navigator.popBackStack()) {
            true
        } else if (scaffoldNavigator.canNavigateBack()) {
            navigateBack()
            true
        } else {
            false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    toAccounts: () -> Unit,
    toAppearance: () -> Unit,
    toStorage: () -> Unit,
    toAbout: () -> Unit,
) {
    val state by producePresenter { settingsPresenter() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = stringResource(id = R.string.settings_title))
                },
            )
        },
    ) {
        Column(
            modifier =
                Modifier
                    .padding(it)
                    .verticalScroll(rememberScrollState()),
        ) {
            AccountItem(
                userState = state.user,
                onClick = {
                    toAccounts.invoke()
                },
                supportingContent = {
                    Text(text = stringResource(id = R.string.settings_accounts_title))
                },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = {
                    Text(text = stringResource(id = R.string.settings_appearance_title))
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = null,
                    )
                },
                supportingContent = {
                    Text(text = stringResource(id = R.string.settings_appearance_subtitle))
                },
                modifier =
                    Modifier.clickable {
                        toAppearance.invoke()
                    },
            )
//            ListItem(
//                headlineContent = {
//                    Text(text = stringResource(id = R.string.settings_notifications_title))
//                },
//                leadingContent = {
//                    Icon(
//                        imageVector = Icons.Default.Notifications,
//                        contentDescription = null,
//                    )
//                },
//                supportingContent = {
//                    Text(text = stringResource(id = R.string.settings_notifications_subtitle))
//                },
//                modifier =
//                    Modifier.clickable {
//                        toNotifications.invoke()
//                    },
//            )
            ListItem(
                headlineContent = {
                    Text(text = stringResource(id = R.string.settings_storage_title))
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = null,
                    )
                },
                supportingContent = {
                    Text(text = stringResource(id = R.string.settings_storage_subtitle))
                },
                modifier =
                    Modifier.clickable {
                        toStorage.invoke()
                    },
            )
            ListItem(
                headlineContent = {
                    Text(text = stringResource(id = R.string.settings_about_title))
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                    )
                },
                supportingContent = {
                    Text(text = stringResource(id = R.string.settings_about_subtitle))
                },
                modifier =
                    Modifier.clickable {
                        toAbout.invoke()
                    },
            )
        }
    }
}

@Composable
private fun settingsPresenter() =
    run {
        val state = remember { ActiveAccountPresenter() }.invoke()
        object : ActiveAccountState by state {
        }
    }
