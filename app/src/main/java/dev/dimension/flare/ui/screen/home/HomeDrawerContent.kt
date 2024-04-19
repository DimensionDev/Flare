package dev.dimension.flare.ui.screen.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ramcosta.composedestinations.generated.destinations.SettingsRouteDestination
import com.ramcosta.composedestinations.spec.DirectionDestinationSpec
import dev.dimension.flare.R
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.home.QuickMenuPresenter
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.screen.settings.AccountItem

@Composable
internal fun HomeDrawerContent(
    currentRoute: String?,
    navigateTo: (DirectionDestinationSpec) -> Unit,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val state by producePresenter("HomeDrawerContent") {
        remember { QuickMenuPresenter() }.invoke()
    }
    var expanded by remember { mutableStateOf(false) }
    ModalDrawerSheet {
        AccountItem(
            state.user,
            onClick = {
                expanded = !expanded
            },
            trailingContent = {
                state.allUsers.onSuccess {
                    if (it.size > 0) {
                        IconButton(
                            onClick = {
                                expanded = !expanded
                            },
                        ) {
                            Icon(
                                if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                contentDescription = null,
                            )
                        }
                    }
                }
            },
        )
        state.allUsers.onSuccess { allUsers ->
            AnimatedVisibility(
                expanded,
                modifier = Modifier.weight(1f),
            ) {
                Column(
                    modifier =
                        Modifier
                            .verticalScroll(rememberScrollState()),
                ) {
                    HorizontalDivider()
                    (0 until allUsers.size).forEach { index ->
                        AccountItem(
                            allUsers[index],
                            onClick = {
                                state.setActiveAccount(it)
                            },
                        )
                    }
                }
            }
        }
        state.user.onSuccess {
            HorizontalDivider()
        }
        AnimatedVisibility(
            visible = !expanded,
            modifier = Modifier.weight(1f),
        ) {
            Column(
                modifier =
                    Modifier
                        .verticalScroll(rememberScrollState()),
            ) {
                content.invoke(this)
            }
        }
        NavigationDrawerItem(
            label = {
                Text(stringResource(R.string.settings_title))
            },
            selected = currentRoute == SettingsRouteDestination.route,
            onClick = {
                navigateTo(SettingsRouteDestination)
            },
            icon = {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = stringResource(R.string.settings_title),
                )
            },
        )
    }
}
