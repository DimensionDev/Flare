package dev.dimension.flare.ui.screen.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.navigation.navigate
import com.ramcosta.composedestinations.spec.DestinationStyle
import dev.dimension.flare.R
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.component.ThemeWrapper
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiUser
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.home.QuickMenuPresenter
import dev.dimension.flare.ui.presenter.home.QuickMenuState
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.settings.ImmutableListWrapper
import dev.dimension.flare.ui.screen.destinations.ServiceSelectRouteDestination
import dev.dimension.flare.ui.screen.destinations.SettingsRouteDestination
import dev.dimension.flare.ui.screen.settings.AccountItem

@Destination(
    style = DestinationStyle.Dialog::class,
    wrappers = [ThemeWrapper::class],
)
@Composable
fun QuickMenuDialogRoute(
    rootNavController: RootNavController,
    navigator: DestinationsNavigator,
) {
    QuickMenuDialog(
        toSettings = {
            rootNavController.navController.navigate(SettingsRouteDestination) {
                popUpTo(rootNavController.navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        },
        addAccount = {
            navigator.navigate(ServiceSelectRouteDestination)
        },
        dismiss = {
            navigator.navigateUp()
        },
    )
}

@Composable
private fun QuickMenuDialog(
    toSettings: () -> Unit,
    addAccount: () -> Unit,
    dismiss: () -> Unit,
) {
    val state by producePresenter { quickMenuPresenter() }
    Dialog(
        onDismissRequest = {
            dismiss.invoke()
        },
    ) {
        AccountQuickMenu(
            activeAccount = state.user,
            allAccounts = state.allUsers,
            onAccountClick = {
                state.setExpanded(false)
                state.setActiveAccount(it)
                dismiss.invoke()
            },
            onAddAccountClick = {
                addAccount.invoke()
            },
            onExpandClick = {
                state.setExpanded(!state.expanded)
            },
            expanded = state.expanded,
            onSettingsClick = {
                toSettings.invoke()
            },
        )
    }
}

@Composable
private fun quickMenuPresenter() =
    run {
        val state = remember { QuickMenuPresenter() }.invoke()
        var expanded by remember { mutableStateOf(false) }
        object : QuickMenuState by state {
            val expanded: Boolean
                get() = expanded

            fun setExpanded(value: Boolean) {
                expanded = value
            }
        }
    }

@Composable
private fun AccountQuickMenu(
    activeAccount: UiState<UiUser>,
    allAccounts: UiState<ImmutableListWrapper<UiState<UiUser>>>,
    onAccountClick: (MicroBlogKey) -> Unit,
    onAddAccountClick: () -> Unit,
    onExpandClick: () -> Unit,
    expanded: Boolean,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier =
            modifier
                .padding(16.dp),
        shape = MaterialTheme.shapes.large,
    ) {
        ListItem(
            headlineContent = {
                Text(text = stringResource(R.string.quick_menu_title))
            },
        )
        HorizontalDivider()
        AccountItem(
            activeAccount,
            onClick = {
                onExpandClick.invoke()
            },
            trailingContent = {
                IconButton(
                    onClick = {
                        onExpandClick.invoke()
                    },
                ) {
                    Icon(
                        if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                        contentDescription = null,
                    )
                }
            },
        )
        allAccounts.onSuccess { allUsers ->
            if (expanded) {
                Column {
                    HorizontalDivider()
                    (0 until allUsers.size).forEach { index ->
                        AccountItem(
                            allUsers[index],
                            onClick = {
                                onAccountClick.invoke(it)
                            },
                        )
                    }
                    ListItem(
                        headlineContent = {
                            Text(text = stringResource(R.string.quick_menu_add_account))
                        },
                        leadingContent = {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                            )
                        },
                        modifier =
                            Modifier
                                .clickable {
                                    onAddAccountClick.invoke()
                                },
                    )
                }
            }
        }
        HorizontalDivider()

        ListItem(
            headlineContent = {
                Text(text = stringResource(R.string.settings_title))
            },
            leadingContent = {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = null,
                )
            },
            modifier =
                Modifier
                    .clickable {
                        onSettingsClick.invoke()
                    },
        )
    }
}
