package dev.dimension.flare.ui.screen.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Check
import compose.icons.fontawesomeicons.solid.Trash
import dev.dimension.flare.R
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareLargeFlexibleTopAppBar
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.model.UiKeywordFilter
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.settings.LocalFilterPresenter
import dev.dimension.flare.ui.theme.first
import dev.dimension.flare.ui.theme.item
import dev.dimension.flare.ui.theme.last
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import dev.dimension.flare.ui.theme.single
import moe.tlaster.precompose.molecule.producePresenter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun LocalFilterEditDialog(
    keyword: String?,
    onBack: () -> Unit,
) {
    val topAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val state by producePresenter {
        presenter(keyword = keyword)
    }
    FlareScaffold(
        topBar = {
            FlareLargeFlexibleTopAppBar(
                title = {
                    Text(
                        text =
                            stringResource(
                                id =
                                    if (keyword == null) {
                                        R.string.local_filter_add_title
                                    } else {
                                        R.string.local_filter_edit_title
                                    },
                            ),
                    )
                },
                navigationIcon = {
                    BackButton(onBack = onBack)
                },
                actions = {
                    IconButton(onClick = {
                        state.save()
                        onBack()
                    }) {
                        FAIcon(
                            FontAwesomeIcons.Solid.Check,
                            contentDescription = stringResource(id = android.R.string.ok),
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
        Column(
            modifier =
                Modifier
                    .padding(contentPadding)
                    .padding(horizontal = screenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                state = state.input,
                placeholder = {
                    Text(text = stringResource(id = R.string.local_filter_keyword_hint))
                },
                label = {
                    Text(text = stringResource(id = R.string.local_filter_keyword))
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                SegmentedListItem(
                    onClick = {
                        state.setForTimeline(!state.forTimeline)
                    },
                    shapes = ListItemDefaults.first(),
                    content = {
                        Text(text = stringResource(id = R.string.local_filter_for_timeline))
                    },
                    trailingContent = {
                        Switch(
                            checked = state.forTimeline,
                            onCheckedChange = state::setForTimeline,
                        )
                    },
                )
                SegmentedListItem(
                    onClick = {
                        state.setForNotification(!state.forNotification)
                    },
                    shapes = ListItemDefaults.item(),
                    content = {
                        Text(text = stringResource(id = R.string.local_filter_for_notification))
                    },
                    trailingContent = {
                        Switch(
                            checked = state.forNotification,
                            onCheckedChange = state::setForNotification,
                        )
                    },
                )
                SegmentedListItem(
                    onClick = {
                        state.setForSearch(!state.forSearch)
                    },
                    shapes = ListItemDefaults.last(),
                    content = {
                        Text(text = stringResource(id = R.string.local_filter_for_search))
                    },
                    trailingContent = {
                        Switch(
                            checked = state.forSearch,
                            onCheckedChange = state::setForSearch,
                        )
                    },
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            if (keyword != null) {
                SegmentedListItem(
                    onClick = {
                        state.delete()
                        onBack()
                    },
                    shapes = ListItemDefaults.single(),
                    content = {
                        Text(
                            text = stringResource(id = R.string.local_filter_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    },
                    trailingContent = {
                        FAIcon(
                            FontAwesomeIcons.Solid.Trash,
                            contentDescription = stringResource(id = R.string.delete),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun presenter(keyword: String?) =
    run {
        val input = rememberTextFieldState(keyword ?: "")
        var forTimeline by remember { mutableStateOf(true) }
        var forNotification by remember { mutableStateOf(true) }
        var forSearch by remember { mutableStateOf(true) }
//    var expiredAt by remember { mutableStateOf<Instant?>(null) }
        val state =
            remember {
                LocalFilterPresenter()
            }.invoke()

        state.items.onSuccess {
            LaunchedEffect(Unit) {
                it.find { it.keyword == keyword }?.let { item ->
                    forTimeline = item.forTimeline
                    forNotification = item.forNotification
                    forSearch = item.forSearch
//                expiredAt = item.expiredAt
                }
            }
        }

        object {
            val input = input
            val forTimeline = forTimeline
            val forNotification = forNotification
            val forSearch = forSearch

            fun setForTimeline(value: Boolean) {
                forTimeline = value
            }

            fun setForNotification(value: Boolean) {
                forNotification = value
            }

            fun setForSearch(value: Boolean) {
                forSearch = value
            }

            fun save() {
                state.add(
                    item =
                        UiKeywordFilter(
                            keyword = input.text.toString(),
                            forTimeline = forTimeline,
                            forNotification = forNotification,
                            forSearch = forSearch,
                            expiredAt = null,
                        ),
                )
            }

            fun delete() {
                if (keyword != null) {
                    state.delete(keyword)
                }
            }
        }
    }
