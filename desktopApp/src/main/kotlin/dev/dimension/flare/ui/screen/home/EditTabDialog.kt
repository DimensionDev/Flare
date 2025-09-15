package dev.dimension.flare.ui.screen.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.dimension.flare.Res
import dev.dimension.flare.cancel
import dev.dimension.flare.data.model.TabItem
import dev.dimension.flare.data.model.TitleType
import dev.dimension.flare.edit_tab_name
import dev.dimension.flare.edit_tab_name_placeholder
import dev.dimension.flare.edit_tab_title
import dev.dimension.flare.edit_tab_with_avatar
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ok
import dev.dimension.flare.ui.component.TabIcon
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.screen.settings.EditTabPresenter
import io.github.composefluent.component.CheckBox
import io.github.composefluent.component.ContentDialog
import io.github.composefluent.component.ContentDialogButton
import io.github.composefluent.component.FlyoutContainer
import io.github.composefluent.component.FlyoutPlacement
import io.github.composefluent.component.SubtleButton
import io.github.composefluent.component.Text
import io.github.composefluent.component.TextField
import moe.tlaster.precompose.molecule.producePresenter
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun EditTabDialog(
    visible: Boolean,
    tabItem: TabItem,
    onDismissRequest: () -> Unit,
    onConfirm: (TabItem) -> Unit,
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
                ContentDialogButton.Primary ->
                    tabItem.metaData
                        .copy(
                            title = TitleType.Text(state.text.text.toString()),
                            icon = state.icon,
                        ).let {
                            if (state.canConfirm) {
                                onConfirm(tabItem.update(metaData = it))
                            }
                        }

                ContentDialogButton.Secondary -> Unit
                ContentDialogButton.Close -> onDismissRequest()
            }
        },
        content = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            ) {
                FlyoutContainer(
                    flyout = {
                        LazyHorizontalGrid(
                            rows = GridCells.FixedSize(48.dp),
                            modifier = Modifier.heightIn(max = 120.dp),
                        ) {
                            items(state.availableIcons) { icon ->
                                SubtleButton(
                                    onClick = {
                                        state.setIcon(icon)
                                    },
                                    iconOnly = true,
                                    modifier = Modifier.padding(4.dp),
                                ) {
                                    TabIcon(
                                        accountType = tabItem.account,
                                        icon = icon,
                                        title = tabItem.metaData.title,
                                    )
                                }
                            }
                        }
                    },
                    placement = FlyoutPlacement.Bottom,
                ) {
                    SubtleButton(
                        onClick = {
                            isFlyoutVisible = true
                        },
                        iconOnly = true,
                    ) {
                        TabIcon(
                            accountType = tabItem.account,
                            icon = state.icon,
                            title = tabItem.metaData.title,
                            size = 64.dp,
                        )
                    }
                }
                if (tabItem.account is AccountType.Specific) {
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
private fun presenter(tabItem: TabItem) =
    run {
        val text = rememberTextFieldState()
        val state =
            remember(tabItem) {
                EditTabPresenter(tabItem)
            }.invoke()
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
        }
    }
