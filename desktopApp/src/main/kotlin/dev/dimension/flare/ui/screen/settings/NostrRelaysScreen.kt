package dev.dimension.flare.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Plus
import compose.icons.fontawesomeicons.solid.Trash
import dev.dimension.flare.LocalWindowPadding
import dev.dimension.flare.Res
import dev.dimension.flare.cancel
import dev.dimension.flare.delete
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ok
import dev.dimension.flare.settings_nostr_relays_add
import dev.dimension.flare.settings_nostr_relays_add_dialog_title
import dev.dimension.flare.settings_nostr_relays_empty
import dev.dimension.flare.settings_nostr_relays_placeholder
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareScrollBar
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.settings.NostrRelaysPresenter
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.AccentButton
import io.github.composefluent.component.CardExpanderItem
import io.github.composefluent.component.ContentDialog
import io.github.composefluent.component.ContentDialogButton
import io.github.composefluent.component.SubtleButton
import io.github.composefluent.component.Text
import io.github.composefluent.component.TextField
import moe.tlaster.precompose.molecule.producePresenter
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun NostrRelaysScreen(accountKey: MicroBlogKey) {
    val state by producePresenter(key = "nostr_relays_$accountKey") {
        remember(accountKey) { NostrRelaysPresenter(accountKey) }.invoke()
    }
    val listState = rememberLazyListState()
    var showAddDialog by remember { mutableStateOf(false) }
    val relayInputState = rememberTextFieldState()

    if (showAddDialog) {
        ContentDialog(
            visible = true,
            title = stringResource(Res.string.settings_nostr_relays_add_dialog_title),
            primaryButtonText = stringResource(Res.string.ok),
            closeButtonText = stringResource(Res.string.cancel),
            onButtonClick = {
                when (it) {
                    ContentDialogButton.Primary -> {
                        relayInputState.text
                            .toString()
                            .trim()
                            .takeIf(String::isNotEmpty)
                            ?.let(state::addRelay)
                        relayInputState.edit {
                            replace(0, length, "")
                        }
                        showAddDialog = false
                    }

                    else -> {
                        relayInputState.edit {
                            replace(0, length, "")
                        }
                        showAddDialog = false
                    }
                }
            },
            content = {
                TextField(
                    state = relayInputState,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    lineLimits = TextFieldLineLimits.SingleLine,
                    placeholder = {
                        Text(stringResource(Res.string.settings_nostr_relays_placeholder))
                    },
                )
            },
        )
    }

    FlareScrollBar(listState) {
        LazyColumn(
            state = listState,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = screenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = LocalWindowPadding.current,
        ) {
            item {
                Row(
                    modifier = Modifier.fillParentMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    AccentButton(
                        onClick = {
                            showAddDialog = true
                        },
                    ) {
                        FAIcon(
                            imageVector = FontAwesomeIcons.Solid.Plus,
                            contentDescription = stringResource(Res.string.settings_nostr_relays_add),
                        )
                        Text(stringResource(Res.string.settings_nostr_relays_add))
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.height(6.dp))
            }
            state.relays.onSuccess { relays ->
                if (relays.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(Res.string.settings_nostr_relays_empty),
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                } else {
                    items(relays, key = { it }) { relay ->
                        CardExpanderItem(
                            heading = {
                                Text(relay, maxLines = 1)
                            },
                            trailing = {
                                SubtleButton(
                                    onClick = {
                                        state.removeRelay(relay)
                                    },
                                    iconOnly = true,
                                ) {
                                    FAIcon(
                                        imageVector = FontAwesomeIcons.Solid.Trash,
                                        contentDescription = stringResource(Res.string.delete),
                                        tint = FluentTheme.colors.system.critical,
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
