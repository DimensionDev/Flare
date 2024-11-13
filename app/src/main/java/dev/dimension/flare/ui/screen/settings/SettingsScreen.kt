package dev.dimension.flare.ui.screen.settings

import android.os.Parcelable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.ThreePaneScaffoldNavigator
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.NavGraphs
import com.ramcosta.composedestinations.generated.destinations.AboutRouteDestination
import com.ramcosta.composedestinations.generated.destinations.AccountsRouteDestination
import com.ramcosta.composedestinations.generated.destinations.AppearanceRouteDestination
import com.ramcosta.composedestinations.generated.destinations.LocalFilterRouteDestination
import com.ramcosta.composedestinations.generated.destinations.StorageRouteDestination
import com.ramcosta.composedestinations.generated.destinations.TabCustomizeRouteDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.navigation.dependency
import com.ramcosta.composedestinations.spec.Direction
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.CircleInfo
import compose.icons.fontawesomeicons.solid.CircleUser
import compose.icons.fontawesomeicons.solid.Database
import compose.icons.fontawesomeicons.solid.Filter
import compose.icons.fontawesomeicons.solid.Palette
import compose.icons.fontawesomeicons.solid.Table
import dev.dimension.flare.R
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.ThemeWrapper
import dev.dimension.flare.ui.model.onError
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.home.ActiveAccountPresenter
import dev.dimension.flare.ui.presenter.home.UserState
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.screen.home.NavigationState
import dev.dimension.flare.ui.screen.home.Router
import kotlinx.parcelize.Parcelize
import moe.tlaster.precompose.molecule.producePresenter

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Destination<RootGraph>(
    wrappers = [ThemeWrapper::class],
)
@Composable
internal fun SettingsRoute(navigationState: NavigationState) {
    val scaffoldNavigator =
        rememberListDetailPaneScaffoldNavigator<SettingsDetailDestination>()
    BackHandler(
        scaffoldNavigator.canNavigateBack(),
    ) {
        scaffoldNavigator.navigateBack()
    }
    ListDetailPaneScaffold(
        directive = scaffoldNavigator.scaffoldDirective,
        value = scaffoldNavigator.scaffoldValue,
        listPane = {
            AnimatedPane {
                SettingsScreen(
                    toAccounts = {
                        scaffoldNavigator.navigateTo(ListDetailPaneScaffoldRole.Detail, SettingsDetailDestination.Accounts)
                    },
                    toAppearance = {
                        scaffoldNavigator.navigateTo(ListDetailPaneScaffoldRole.Detail, SettingsDetailDestination.Appearance)
                    },
                    toStorage = {
                        scaffoldNavigator.navigateTo(ListDetailPaneScaffoldRole.Detail, SettingsDetailDestination.Storage)
                    },
                    toAbout = {
                        scaffoldNavigator.navigateTo(ListDetailPaneScaffoldRole.Detail, SettingsDetailDestination.About)
                    },
                    toTabCustomization = {
                        scaffoldNavigator.navigateTo(ListDetailPaneScaffoldRole.Detail, SettingsDetailDestination.TabCustomization)
                    },
                    toLocalFilter = {
                        scaffoldNavigator.navigateTo(ListDetailPaneScaffoldRole.Detail, SettingsDetailDestination.LocalFilter)
                    },
                )
            }
        },
        detailPane = {
            AnimatedPane {
                scaffoldNavigator.currentDestination?.content?.let { item ->
                    Router(navGraph = NavGraphs.root, item.toDestination()) {
                        dependency(
                            ProxyDestinationsNavigator(
                                scaffoldNavigator,
                                destinationsNavigator,
                                navigateBack = {
                                    scaffoldNavigator.navigateBack()
                                },
                            ),
                        )
                        dependency(navigationState)
                    }
                }
            }
        },
    )
}

@Parcelize
internal enum class SettingsDetailDestination : Parcelable {
    Accounts,
    Appearance,
    Storage,
    About,
    TabCustomization,
    LocalFilter,
    ;

    fun toDestination(): Direction =
        when (this) {
            Accounts -> AccountsRouteDestination
            Appearance -> AppearanceRouteDestination
            Storage -> StorageRouteDestination
            About -> AboutRouteDestination
            TabCustomization -> TabCustomizeRouteDestination
            LocalFilter -> LocalFilterRouteDestination
        }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
internal class ProxyDestinationsNavigator(
    private val scaffoldNavigator: ThreePaneScaffoldNavigator<SettingsDetailDestination>,
    private val navigator: DestinationsNavigator,
    private val navigateBack: () -> Unit,
) : DestinationsNavigator by navigator {
    override fun navigateUp(): Boolean =
        if (navigator.navigateUp()) {
            true
        } else if (scaffoldNavigator.canNavigateBack()) {
            navigateBack()
            true
        } else {
            false
        }

    override fun popBackStack(): Boolean =
        if (navigator.popBackStack()) {
            true
        } else if (scaffoldNavigator.canNavigateBack()) {
            navigateBack()
            true
        } else {
            false
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    toAccounts: () -> Unit,
    toAppearance: () -> Unit,
    toStorage: () -> Unit,
    toAbout: () -> Unit,
    toTabCustomization: () -> Unit,
    toLocalFilter: () -> Unit,
) {
    val state by producePresenter { settingsPresenter() }
    FlareScaffold(
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
            state.user
                .onSuccess {
                    AccountItem(
                        userState = state.user,
                        onClick = {
                            toAccounts.invoke()
                        },
                        supportingContent = {
                            Text(text = stringResource(id = R.string.settings_accounts_title))
                        },
                        toLogin = {
                            toAccounts.invoke()
                        },
                    )
                }.onError {
                    ListItem(
                        headlineContent = {
                            Text(text = stringResource(id = R.string.settings_accounts_title))
                        },
                        modifier =
                            Modifier
                                .clickable {
                                    toAccounts.invoke()
                                },
                        leadingContent = {
                            FAIcon(
                                imageVector = FontAwesomeIcons.Solid.CircleUser,
                                contentDescription = null,
                            )
                        },
                        supportingContent = {
                            Text(text = stringResource(id = R.string.settings_accounts_title))
                        },
                    )
                }
            HorizontalDivider()
            ListItem(
                headlineContent = {
                    Text(text = stringResource(id = R.string.settings_appearance_title))
                },
                leadingContent = {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.Palette,
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
            state.user.onSuccess {
                ListItem(
                    headlineContent = {
                        Text(text = stringResource(id = R.string.settings_tab_customization))
                    },
                    leadingContent = {
                        FAIcon(
                            imageVector = FontAwesomeIcons.Solid.Table,
                            contentDescription = null,
                        )
                    },
                    supportingContent = {
                        Text(text = stringResource(id = R.string.settings_tab_customization_description))
                    },
                    modifier =
                        Modifier.clickable {
                            toTabCustomization.invoke()
                        },
                )
                ListItem(
                    headlineContent = {
                        Text(text = stringResource(id = R.string.settings_local_filter_title))
                    },
                    leadingContent = {
                        FAIcon(
                            imageVector = FontAwesomeIcons.Solid.Filter,
                            contentDescription = null,
                        )
                    },
                    supportingContent = {
                        Text(text = stringResource(id = R.string.settings_local_filter_description))
                    },
                    modifier =
                        Modifier.clickable {
                            toLocalFilter.invoke()
                        },
                )
            }
//            ListItem(
//                headlineContent = {
//                    Text(text = stringResource(id = R.string.settings_notifications_title))
//                },
//                leadingContent = {
//                    FAIcon(
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
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.Database,
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
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.CircleInfo,
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
        object : UserState by state {
        }
    }
