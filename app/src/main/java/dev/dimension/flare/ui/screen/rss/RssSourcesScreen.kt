package dev.dimension.flare.ui.screen.rss

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.EllipsisVertical
import compose.icons.fontawesomeicons.solid.File
import compose.icons.fontawesomeicons.solid.Pen
import compose.icons.fontawesomeicons.solid.Plus
import compose.icons.fontawesomeicons.solid.Trash
import dev.dimension.flare.R
import dev.dimension.flare.ui.common.items
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.FlareTopAppBar
import dev.dimension.flare.ui.component.NetworkImage
import dev.dimension.flare.ui.model.UiRssSource
import dev.dimension.flare.ui.presenter.home.rss.RssSourcesPresenter
import dev.dimension.flare.ui.presenter.invoke
import moe.tlaster.precompose.molecule.producePresenter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RssSourcesScreen(
    onAdd: () -> Unit,
    onEdit: (Int) -> Unit,
    onClicked: (UiRssSource) -> Unit,
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
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            items(
                state.sources,
                emptyContent = {
                    Column(
                        modifier =
                            Modifier
                                .fillParentMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        FAIcon(
                            FontAwesomeIcons.Solid.File,
                            contentDescription = stringResource(R.string.empty_rss_sources),
                            modifier = Modifier.size(48.dp),
                        )
                        Text(
                            text = stringResource(R.string.empty_rss_sources),
                            style = MaterialTheme.typography.headlineMedium,
                        )
                    }
                },
            ) {
                ListItem(
                    modifier =
                        Modifier
                            .clickable {
                                onClicked.invoke(it)
                            },
                    headlineContent = {
                        it.title?.let {
                            Text(text = it)
                        }
                    },
                    supportingContent = {
                        Text(it.url)
                    },
                    leadingContent = {
                        NetworkImage(
                            model = it.favIcon,
                            contentDescription = it.title,
                            modifier = Modifier.size(24.dp),
                        )
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
        val state = remember { RssSourcesPresenter() }.invoke()

        object : RssSourcesPresenter.State by state {
        }
    }
