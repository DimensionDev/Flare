package dev.dimension.flare.ui.screen.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.dimension.flare.Res
import dev.dimension.flare.cancel
import dev.dimension.flare.data.model.tab.TimelineTabItemV2
import dev.dimension.flare.edit_tab_name
import dev.dimension.flare.edit_tab_name_placeholder
import dev.dimension.flare.edit_tab_title
import dev.dimension.flare.edit_tab_with_avatar
import dev.dimension.flare.ok
import dev.dimension.flare.ui.component.TabIcon
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.screen.settings.EditTabPresenter
import io.github.composefluent.component.CheckBox
import io.github.composefluent.component.ContentDialog
import io.github.composefluent.component.ContentDialogButton
import io.github.composefluent.component.Flyout
import io.github.composefluent.component.FlyoutPlacement
import io.github.composefluent.component.SubtleButton
import io.github.composefluent.component.Text
import io.github.composefluent.component.TextField
import moe.tlaster.precompose.molecule.producePresenter
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun EditTabDialog(
    visible: Boolean,
    tabItem: TimelineTabItemV2,
    onDismissRequest: () -> Unit,
    onConfirm: (TimelineTabItemV2) -> Unit,
) {
    val state by producePresenter(key = "EditTabSheet_$tabItem") {
        presenter(tabItem = tabItem)
    }
    ContentDialog(
        visible = visible,
        title = stringResource(Res.string.edit_tab_title),
        primaryButtonText = stringResource(Res.string.ok),
        closeButtonText = stringResource(Res.string.cancel),
        onButtonClick = {
            when (it) {
                ContentDialogButton.Primary -> {
                    if (state.canConfirm) {
                        onConfirm(
                            tabItem.withPresentationOverrides(
                                title = state.text.text.toString(),
                                icon = state.icon,
                            ),
                        )
                    }
                }

                ContentDialogButton.Secondary -> {
                    Unit
                }

                ContentDialogButton.Close -> {
                    onDismissRequest()
                }
            }
        },
        content = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            ) {
                SubtleButton(
                    onClick = {
                        state.setShowIconPicker(true)
                    },
                    iconOnly = true,
                ) {
                    TabIcon(
                        tabItem = tabItem,
                        icon = state.icon,
                        title = tabItem.title,
                        size = 64.dp,
                    )
                }
                Flyout(
                    visible = state.showIconPicker,
                    onDismissRequest = {
                        state.setShowIconPicker(false)
                    },
                    placement = FlyoutPlacement.Bottom,
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.FixedSize(48.dp),
                        modifier = Modifier.heightIn(max = 120.dp),
                    ) {
                        items(state.availableIcons) { icon ->
                            SubtleButton(
                                onClick = {
                                    state.setIcon(icon)
                                    state.setShowIconPicker(false)
                                },
                                iconOnly = true,
                                modifier = Modifier.padding(4.dp),
                            ) {
                                TabIcon(
                                    tabItem = tabItem,
                                    icon = icon,
                                    title = tabItem.title,
                                )
                            }
                        }
                    }
                }
                if (state.canUseAvatar) {
                    Row(
                        modifier =
                            Modifier
                                .clickable {
                                    state.setWithAvatar(!state.withAvatar)
                                },
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CheckBox(
                            checked = state.withAvatar,
                            onCheckStateChange = state::setWithAvatar,
                        )
                        Text(text = stringResource(Res.string.edit_tab_with_avatar))
                    }
                }
                TextField(
                    state = state.text,
                    modifier = Modifier.fillMaxWidth(),
                    header = {
                        Text(text = stringResource(Res.string.edit_tab_name))
                    },
                    placeholder = {
                        Text(text = stringResource(Res.string.edit_tab_name_placeholder))
                    },
                )
            }
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
