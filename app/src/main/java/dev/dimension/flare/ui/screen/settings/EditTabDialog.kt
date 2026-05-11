package dev.dimension.flare.ui.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
import dev.dimension.flare.data.model.tab.TimelineTabItemV2
import dev.dimension.flare.ui.component.TabIcon
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.invoke
import moe.tlaster.precompose.molecule.producePresenter

@Composable
internal fun EditTabDialog(
    tabItem: TimelineTabItemV2,
    onDismissRequest: () -> Unit,
    onConfirm: (TimelineTabItemV2) -> Unit,
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
                    onConfirm(
                        tabItem.withPresentationOverrides(
                            title = state.text.text.toString(),
                            icon = state.icon,
                        ),
                    )
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
                    tabItem = tabItem,
                    icon = state.icon,
                    title = tabItem.title,
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
                            LazyVerticalGrid(
                                columns = GridCells.FixedSize(48.dp),
                            ) {
                                items(state.availableIcons) { icon ->
                                    TabIcon(
                                        tabItem = tabItem,
                                        icon = icon,
                                        title = tabItem.title,
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

                if (state.canUseAvatar) {
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
private fun presenter(tabItem: TimelineTabItemV2) =
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
