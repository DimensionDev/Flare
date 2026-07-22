package dev.dimension.flare.ui.screen.settings

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Pen
import compose.icons.fontawesomeicons.solid.Plus
import dev.dimension.flare.R
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareLargeFlexibleTopAppBar
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.status.ListEmptyView
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.settings.LocalFilterPresenter
import dev.dimension.flare.ui.presenter.settings.LocalFilterState
import dev.dimension.flare.ui.presenter.settings.MxgaSettingsPresenter
import dev.dimension.flare.ui.presenter.settings.MxgaSettingsState
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import dev.dimension.flare.ui.theme.segmentedShapes2
import moe.tlaster.precompose.molecule.producePresenter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LocalFilterScreen(
    onBack: () -> Unit,
    edit: (String) -> Unit,
    add: () -> Unit,
) {
    val topAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val state by producePresenter {
        presenter()
    }

    FlareScaffold(
        topBar = {
            FlareLargeFlexibleTopAppBar(
                title = {
                    Text(text = stringResource(id = R.string.settings_local_filter_title))
                },
                navigationIcon = {
                    BackButton(onBack = onBack)
                },
                actions = {
                    IconButton(onClick = add) {
                        FAIcon(
                            FontAwesomeIcons.Solid.Plus,
                            contentDescription = stringResource(id = R.string.local_filter_add),
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
            contentPadding = contentPadding,
            modifier =
                Modifier
                    .padding(horizontal = screenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            if (state.mxgaState.hasXQtAccount) {
                item {
                    MxgaSettingsSection(state.mxgaState)
                }
            }
            state.items.onSuccess { list ->
                if (list.isEmpty() && !state.mxgaState.hasXQtAccount) {
                    item {
                        ListEmptyView(
                            modifier = Modifier.fillParentMaxSize(),
                        )
                    }
                } else {
                    items(list.size) { index ->
                        val item = list[index]
                        SegmentedListItem(
                            onClick = {},
                            shapes = ListItemDefaults.segmentedShapes2(index, list.size),
                            content = {
                                Text(text = item.keyword)
                            },
                            supportingContent =
                                item.isRegex.takeIf { it }?.let {
                                    {
                                        Text(text = stringResource(id = R.string.local_filter_regex))
                                    }
                                },
                            trailingContent = {
                                IconButton(onClick = {
                                    edit(item.keyword)
                                }) {
                                    FAIcon(
                                        FontAwesomeIcons.Solid.Pen,
                                        contentDescription = stringResource(id = R.string.local_filter_edit_title),
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

@Composable
private fun MxgaSettingsSection(state: MxgaSettingsState) {
    val uriHandler = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        SegmentedListItem(
            onClick = { state.setEnabled(!state.isEnabled) },
            shapes = ListItemDefaults.segmentedShapes2(0, 2),
            content = {
                Text(text = stringResource(id = R.string.settings_mxga_filter_title))
            },
            supportingContent = {
                Text(text = stringResource(id = R.string.settings_mxga_filter_description))
            },
            trailingContent = {
                Switch(
                    checked = state.isEnabled,
                    onCheckedChange = state::setEnabled,
                )
            },
        )
        SegmentedListItem(
            onClick = state::refresh,
            shapes = ListItemDefaults.segmentedShapes2(1, 2),
            content = {
                Text(text = stringResource(id = R.string.settings_mxga_refresh_title))
            },
            supportingContent = {
                Text(text = mxgaStatusText(state))
            },
            trailingContent = {
                if (state.isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                    )
                }
            },
        )
        TextButton(
            onClick = {
                uriHandler.openUri("https://github.com/foru17/make-x-great-again")
            },
        ) {
            Text(text = stringResource(id = R.string.settings_mxga_learn_more))
        }
    }
}

@Composable
private fun presenter() =
    run {
        val localFilterState = remember { LocalFilterPresenter() }.invoke()
        val mxgaState = remember { MxgaSettingsPresenter() }.invoke()
        object : LocalFilterState by localFilterState {
            val mxgaState = mxgaState
        }
    }

@Composable
private fun mxgaStatusText(state: MxgaSettingsState): String =
    when {
        state.isRefreshing -> {
            stringResource(id = R.string.settings_mxga_refreshing)
        }

        state.lastCheckedAt <= 0L -> {
            stringResource(id = R.string.settings_mxga_never_refreshed)
        }

        else -> {
            val lastChecked =
                DateUtils
                    .getRelativeTimeSpanString(
                        state.lastCheckedAt,
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS,
                    ).toString()
            stringResource(
                id = R.string.settings_mxga_last_refreshed,
                lastChecked,
            )
        }
    }
