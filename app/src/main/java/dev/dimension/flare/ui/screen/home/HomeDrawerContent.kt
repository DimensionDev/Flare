package dev.dimension.flare.ui.screen.home

import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import dev.dimension.flare.R
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.HtmlText2
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.home.QuickMenuPresenter

@Composable
internal fun HomeDrawerContent(toSettings: () -> Unit) {
    val state by producePresenter("HomeDrawerContent") {
        remember { QuickMenuPresenter() }.invoke()
    }
    var expanded by remember { mutableStateOf(false) }
    ModalDrawerSheet {
        state.user.onSuccess { user ->
            NavigationDrawerItem(
                label = {
                    HtmlText2(user.nameElement)
                },
                selected = false,
                onClick = {
                    expanded = !expanded
                },
                icon = {
                    AvatarComponent(
                        data = user.avatarUrl,
                    )
                },
            )
        }
        state.allUsers.onSuccess { allUsers ->
            if (expanded && allUsers.size > 0) {
                HorizontalDivider()
                (0 until allUsers.size).forEach {
                    val data = allUsers[it]
                    data.onSuccess { user ->
                        NavigationDrawerItem(
                            label = {
                                HtmlText2(user.nameElement)
                            },
                            selected = false,
                            onClick = {
                                state.setActiveAccount(user.userKey)
                            },
                            icon = {
                                AvatarComponent(
                                    data = user.avatarUrl,
                                )
                            },
                        )
                    }
                }
            }
        }
        HorizontalDivider()
        Spacer(modifier = androidx.compose.ui.Modifier.weight(1f))
        NavigationDrawerItem(
            label = {
                Text(stringResource(R.string.settings_title))
            },
            selected = false,
            onClick = toSettings,
            icon = {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = stringResource(R.string.settings_title),
                )
            },
        )
    }
}
