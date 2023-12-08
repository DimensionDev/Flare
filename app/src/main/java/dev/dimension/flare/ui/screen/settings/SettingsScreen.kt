package dev.dimension.flare.ui.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.dimension.flare.R
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.component.ThemeWrapper
import dev.dimension.flare.ui.presenter.home.ActiveAccountPresenter
import dev.dimension.flare.ui.presenter.home.ActiveAccountState
import dev.dimension.flare.ui.screen.destinations.AboutRouteDestination
import dev.dimension.flare.ui.screen.destinations.AccountsRouteDestination
import dev.dimension.flare.ui.screen.destinations.AppearanceRouteDestination
import dev.dimension.flare.ui.screen.destinations.StorageRouteDestination

@Destination(
    wrappers = [ThemeWrapper::class],
)
@Composable
fun SettingsRoute(navigator: DestinationsNavigator) {
    SettingsScreen(
        onBack = navigator::navigateUp,
        toAccounts = {
            navigator.navigate(AccountsRouteDestination)
        },
        toAppearance = {
            navigator.navigate(AppearanceRouteDestination)
        },
        toNotifications = {},
        toStorage = {
            navigator.navigate(StorageRouteDestination)
        },
        toAbout = {
            navigator.navigate(AboutRouteDestination)
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    onBack: () -> Unit,
    toAccounts: () -> Unit,
    toAppearance: () -> Unit,
    toNotifications: () -> Unit,
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
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.navigate_back),
                        )
                    }
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
