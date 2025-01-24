package dev.dimension.flare.ui.screen.rss

import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.EllipsisVertical
import compose.icons.fontawesomeicons.solid.Pen
import compose.icons.fontawesomeicons.solid.Plus
import compose.icons.fontawesomeicons.solid.Trash
import dev.dimension.flare.R
import dev.dimension.flare.ui.common.items
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.FlareTopAppBar
import dev.dimension.flare.ui.presenter.home.rss.RssSourcesPresenter
import dev.dimension.flare.ui.presenter.invoke
import moe.tlaster.precompose.molecule.producePresenter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RssSourcesScreen(
    onAdd: () -> Unit,
    onEdit: (Int) -> Unit,
    onClicked: (Int) -> Unit,
) {
    val state by producePresenter { presenter() }
    FlareScaffold(
        topBar = {
            FlareTopAppBar(
                title = {
                    Text(text = stringResource(R.string.rss_sources_title))
                },
                actions = {
                    IconButton(
                        onClick = {
                            onAdd.invoke()
                        },
                    ) {
                        FAIcon(
                            FontAwesomeIcons.Solid.Plus,
                            contentDescription = stringResource(R.string.add_rss_source),
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        LazyColumn(
            contentPadding = contentPadding,
        ) {
            items(state.sources) {
                ListItem(
                    modifier =
                        Modifier
                            .clickable {
                                onClicked.invoke(it.id)
                            },
                    headlineContent = {
                        it.title?.let {
                            Text(text = it)
                        }
                    },
                    supportingContent = {
                        Text(it.url)
                    },
                    trailingContent = {
                        var showDropdown by remember {
                            mutableStateOf(false)
                        }
                        IconButton(onClick = { showDropdown = true }) {
                            FAIcon(
                                imageVector = FontAwesomeIcons.Solid.EllipsisVertical,
                                contentDescription = stringResource(id = R.string.more),
                            )
                            DropdownMenu(
                                expanded = showDropdown,
                                onDismissRequest = { showDropdown = false },
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = stringResource(id = R.string.edit_rss_source),
                                        )
                                    },
                                    onClick = {
                                        onEdit.invoke(it.id)
                                        showDropdown = false
                                    },
                                    leadingIcon = {
                                        FAIcon(
                                            imageVector = FontAwesomeIcons.Solid.Pen,
                                            contentDescription = stringResource(id = R.string.edit_rss_source),
                                        )
                                    },
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = stringResource(id = R.string.delete_rss_source),
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    },
                                    onClick = {
                                        state.delete(it.id)
                                        showDropdown = false
                                    },
                                    leadingIcon = {
                                        FAIcon(
                                            imageVector = FontAwesomeIcons.Solid.Trash,
                                            contentDescription = stringResource(id = R.string.delete_rss_source),
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    },
                                )
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun presenter() =
    run {
        remember { RssSourcesPresenter() }.invoke()
    }
