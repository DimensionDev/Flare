package dev.dimension.flare.ui.screen.rss

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.EllipsisVertical
import compose.icons.fontawesomeicons.solid.File
import compose.icons.fontawesomeicons.solid.Pen
import compose.icons.fontawesomeicons.solid.Plus
import compose.icons.fontawesomeicons.solid.Thumbtack
import compose.icons.fontawesomeicons.solid.ThumbtackSlash
import compose.icons.fontawesomeicons.solid.Trash
import dev.dimension.flare.R
import dev.dimension.flare.data.model.RssTimelineTabItem
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.ui.common.itemsIndexed
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareDropdownMenu
import dev.dimension.flare.ui.component.FlareLargeFlexibleTopAppBar
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.NetworkImage
import dev.dimension.flare.ui.component.listCard
import dev.dimension.flare.ui.model.UiRssSource
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.home.rss.RssSourcesPresenter
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RssSourcesScreen(
    onAdd: () -> Unit,
    onEdit: (Int) -> Unit,
    onClicked: (UiRssSource) -> Unit,
) {
    val topAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val state by producePresenter { presenter() }
    FlareScaffold(
        topBar = {
            FlareLargeFlexibleTopAppBar(
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
                scrollBehavior = topAppBarScrollBehavior,
            )
        },
        modifier =
            Modifier
                .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
    ) { contentPadding ->
        LazyColumn(
            modifier =
                Modifier
                    .padding(horizontal = screenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = contentPadding,
        ) {
            itemsIndexed(
                state.sources,
                emptyContent = {
                    Column(
                        modifier =
                            Modifier
                                .fillParentMaxSize(),
                        verticalArrangement =
                            Arrangement.spacedBy(
                                8.dp,
                                Alignment.CenterVertically,
                            ),
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
            ) { index, itemCount, it ->
                ListItem(
                    modifier =
                        Modifier
                            .clickable {
                                onClicked.invoke(it)
                            }.listCard(
                                index = index,
                                totalCount = itemCount,
                            ),
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            state.currentTabs.onSuccess { currentTabs ->
                                val isPinned =
                                    remember(
                                        it,
                                        currentTabs,
                                    ) {
                                        currentTabs.contains(it.url)
                                    }
                                IconButton(
                                    onClick = {
                                        if (isPinned) {
                                            state.unpinSource(it)
                                        } else {
                                            state.pinSource(it)
                                        }
                                    },
                                ) {
                                    AnimatedContent(isPinned) {
                                        if (it) {
                                            FAIcon(
                                                imageVector = FontAwesomeIcons.Solid.ThumbtackSlash,
                                                contentDescription = stringResource(id = R.string.tab_settings_add),
                                            )
                                        } else {
                                            FAIcon(
                                                imageVector = FontAwesomeIcons.Solid.Thumbtack,
                                                contentDescription = stringResource(id = R.string.tab_settings_remove),
                                            )
                                        }
                                    }
                                }
                            }

                            var showDropdown by remember {
                                mutableStateOf(false)
                            }
                            IconButton(onClick = { showDropdown = true }) {
                                FAIcon(
                                    imageVector = FontAwesomeIcons.Solid.EllipsisVertical,
                                    contentDescription = stringResource(id = R.string.more),
                                )
                                FlareDropdownMenu(
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
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun presenter(
    settingsRepository: SettingsRepository = koinInject(),
    appScope: CoroutineScope = koinInject(),
) = run {
    val state = remember { RssSourcesPresenter() }.invoke()
    val tabSettings by settingsRepository.tabSettings.collectAsUiState()
    val currentTabs =
        tabSettings.map {
            it.mainTabs
                .filterIsInstance<RssTimelineTabItem>()
                .map { it.feedUrl }
                .toImmutableList()
        }
    object : RssSourcesPresenter.State by state {
        val currentTabs = currentTabs

        fun pinSource(source: UiRssSource) {
            appScope.launch {
                settingsRepository.updateTabSettings {
                    copy(
                        mainTabs =
                            mainTabs + RssTimelineTabItem(source),
                    )
                }
            }
        }

        fun unpinSource(source: UiRssSource) {
            appScope.launch {
                settingsRepository.updateTabSettings {
                    copy(
                        mainTabs =
                            mainTabs.filterNot { it is RssTimelineTabItem && it.feedUrl == source.url },
                    )
                }
            }
        }
    }
}
