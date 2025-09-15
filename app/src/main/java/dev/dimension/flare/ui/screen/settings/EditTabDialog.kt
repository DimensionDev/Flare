package dev.dimension.flare.ui.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import dev.dimension.flare.R
import dev.dimension.flare.data.model.TabItem
import dev.dimension.flare.data.model.TitleType
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.component.TabIcon
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.invoke
import moe.tlaster.precompose.molecule.producePresenter

@Composable
internal fun EditTabDialog(
    tabItem: TabItem,
    onDismissRequest: () -> Unit,
    onConfirm: (TabItem) -> Unit,
) {
    val state by producePresenter(key = "EditTabSheet_$tabItem") {
        presenter(tabItem = tabItem)
    }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                enabled = state.canConfirm,
                onClick = {
                    tabItem.metaData
                        .copy(
                            title = TitleType.Text(state.text.text.toString()),
                            icon = state.icon,
                        ).let {
                            onConfirm(tabItem.update(metaData = it))
                        }
                },
            ) {
                Text(text = stringResource(id = android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(id = android.R.string.cancel))
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TabIcon(
                    accountType = tabItem.account,
                    icon = state.icon,
                    title = tabItem.metaData.title,
                    size = 64.dp,
                    modifier =
                        Modifier
                            .clickable {
                                state.setShowIconPicker(true)
                            },
                )
                if (state.showIconPicker) {
                    Popup(
                        onDismissRequest = {
                            state.setShowIconPicker(false)
                        },
                        alignment = Alignment.BottomCenter,
                        properties =
                            PopupProperties(
                                usePlatformDefaultWidth = true,
                                focusable = true,
                            ),
                    ) {
                        Card(
                            modifier =
                                Modifier
                                    .sizeIn(
                                        maxHeight = 256.dp,
                                        maxWidth = 384.dp,
                                    ),
                            elevation =
                                CardDefaults.elevatedCardElevation(
                                    defaultElevation = 3.dp,
                                ),
                        ) {
                            LazyHorizontalGrid(
                                rows = GridCells.FixedSize(48.dp),
                            ) {
                                items(state.availableIcons) { icon ->
                                    TabIcon(
                                        accountType = tabItem.account,
                                        icon = icon,
                                        title = tabItem.metaData.title,
                                        modifier =
                                            Modifier
                                                .padding(4.dp)
                                                .clickable {
                                                    state.setIcon(icon)
                                                    state.setShowIconPicker(false)
                                                },
                                        size = 48.dp,
                                    )
                                }
                            }
                        }
                    }
                }

                if (tabItem.account is AccountType.Specific) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    state.setWithAvatar(!state.withAvatar)
                                },
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = state.withAvatar,
                            onCheckedChange = state::setWithAvatar,
                        )
                        Text(text = stringResource(id = R.string.edit_tab_with_avatar))
                    }
                }

                OutlinedTextField(
                    state = state.text,
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text(text = stringResource(id = R.string.edit_tab_name))
                    },
                    placeholder = {
                        Text(text = stringResource(id = R.string.edit_tab_name_placeholder))
                    },
                )
            }
        },
        title = {
            Text(text = stringResource(id = R.string.edit_tab_title))
        },
    )
}

@Composable
private fun presenter(tabItem: TabItem) =
    run {
        val text = rememberTextFieldState()
        val state =
            remember(tabItem) {
                EditTabPresenter(tabItem)
            }.invoke()
        var showIconPicker by remember { mutableStateOf(false) }
        state.initialText.onSuccess {
            LaunchedEffect(it) {
                text.edit {
                    append(it)
                }
            }
        }
        object : EditTabPresenter.State by state {
            val text = text
            val canConfirm = text.text.isNotEmpty()
            val showIconPicker = showIconPicker

            fun setShowIconPicker(value: Boolean) {
                showIconPicker = value
            }
        }
    }
