package dev.dimension.flare.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Plus
import compose.icons.fontawesomeicons.solid.Trash
import dev.dimension.flare.R
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareLargeFlexibleTopAppBar
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.model.takeSuccess
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.settings.NostrRelaysPresenter
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import dev.dimension.flare.ui.theme.segmentedShapes2
import dev.dimension.flare.ui.theme.single
import moe.tlaster.precompose.molecule.producePresenter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NostrRelaysScreen(
    accountKey: MicroBlogKey,
    onBack: () -> Unit,
) {
    val state by producePresenter(key = "nostr_relays_$accountKey") {
        remember(accountKey) { NostrRelaysPresenter(accountKey) }.invoke()
    }
    val topAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var showAddDialog by remember { mutableStateOf(false) }
    var relay by remember { mutableStateOf("") }
    val relays = state.relays.takeSuccess()

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddDialog = false
                relay = ""
            },
            title = {
                Text(text = stringResource(id = R.string.settings_nostr_relays_add_dialog_title))
            },
            text = {
                OutlinedTextField(
                    value = relay,
                    onValueChange = {
                        relay = it
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(text = stringResource(id = R.string.settings_nostr_relays_placeholder))
                    },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        relay
                            .trim()
                            .takeIf(String::isNotEmpty)
                            ?.let(state::addRelay)
                        showAddDialog = false
                        relay = ""
                    },
                ) {
                    Text(text = stringResource(id = android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAddDialog = false
                        relay = ""
                    },
                ) {
                    Text(text = stringResource(id = android.R.string.cancel))
                }
            },
        )
    }

    FlareScaffold(
        topBar = {
            FlareLargeFlexibleTopAppBar(
                title = {
                    Text(text = stringResource(id = R.string.settings_nostr_relays_manage))
                },
                navigationIcon = {
                    BackButton(onBack = onBack)
                },
                actions = {
                    IconButton(
                        onClick = {
                            showAddDialog = true
                        },
                    ) {
                        FAIcon(
                            imageVector = FontAwesomeIcons.Solid.Plus,
                            contentDescription = stringResource(id = R.string.settings_nostr_relays_add),
                        )
                    }
                },
                scrollBehavior = topAppBarScrollBehavior,
            )
        },
        modifier =
            Modifier
                .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
    ) {
        LazyColumn(
            contentPadding = it,
            modifier =
                Modifier
                    .padding(horizontal = screenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            if (relays.isNullOrEmpty()) {
                item {
                    SegmentedListItem(
                        onClick = {},
                        shapes = ListItemDefaults.single(),
                        content = {
                            Text(text = stringResource(id = R.string.settings_nostr_relays_empty))
                        },
                    )
                }
            } else {
                itemsIndexed(relays, key = { _, item -> item }) { index, item ->
                    SegmentedListItem(
                        onClick = {},
                        shapes = ListItemDefaults.segmentedShapes2(index, relays.size),
                        content = {
                            Text(text = item, maxLines = 1)
                        },
                        trailingContent = {
                            IconButton(
                                onClick = {
                                    state.removeRelay(item)
                                },
                            ) {
                                FAIcon(
                                    imageVector = FontAwesomeIcons.Solid.Trash,
                                    contentDescription = stringResource(id = R.string.delete),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}
