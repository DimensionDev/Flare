package dev.dimension.flare.ui.screen.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dimension.flare.R
import dev.dimension.flare.data.model.tab.TimelineFilterConfig
import dev.dimension.flare.data.model.tab.TimelinePostContent
import dev.dimension.flare.data.model.tab.TimelinePostKind
import dev.dimension.flare.ui.theme.segmentedShapes2

@Composable
internal fun TimelineFilterSettingsItem(
    filterConfig: TimelineFilterConfig,
    onClick: () -> Unit,
    shapes: ListItemShapes,
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = shapes,
        content = {
            Text(text = stringResource(id = R.string.tab_settings_filter_title))
        },
        supportingContent = {
            Text(text = stringResource(id = R.string.tab_settings_filter_desc))
        },
    )
}

@Composable
internal fun TimelineFilterDialog(
    filterConfig: TimelineFilterConfig,
    onDismissRequest: () -> Unit,
    onConfirm: (TimelineFilterConfig) -> Unit,
) {
    val kindOptions =
        remember {
            listOf(
                TimelinePostKind.Reply,
                TimelinePostKind.Repost,
                TimelinePostKind.Quote,
            )
        }
    val contentOptions =
        remember {
            listOf(
                TimelinePostContent.Text,
                TimelinePostContent.Image,
                TimelinePostContent.Video,
            )
        }
    var selectedKinds by remember(filterConfig) {
        mutableStateOf(kindOptions.filterNot { it in filterConfig.excludedKinds }.toSet())
    }
    var selectedContents by remember(filterConfig) {
        mutableStateOf(contentOptions.filterNot { it in filterConfig.excludedContents }.toSet())
    }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(text = stringResource(id = R.string.tab_settings_filter_title))
        },
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                FilterSection(
                    title = stringResource(id = R.string.tab_settings_filter_kind_group),
                    options = kindOptions,
                    selected = selectedKinds,
                    label = ::filterKindLabel,
                    onToggle = { option ->
                        selectedKinds =
                            if (option in selectedKinds) {
                                selectedKinds - option
                            } else {
                                selectedKinds + option
                            }
                    },
                )
                FilterSection(
                    title = stringResource(id = R.string.tab_settings_filter_content_group),
                    options = contentOptions,
                    selected = selectedContents,
                    label = ::filterContentLabel,
                    onToggle = { option ->
                        selectedContents =
                            if (option in selectedContents) {
                                selectedContents - option
                            } else {
                                selectedContents + option
                            }
                    },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        TimelineFilterConfig(
                            excludedKinds = kindOptions.filterNot { it in selectedKinds },
                            excludedContents = contentOptions.filterNot { it in selectedContents },
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
    )
}

@Composable
private fun <T> FilterSection(
    title: String,
    options: List<T>,
    selected: Set<T>,
    label: @Composable (T) -> String,
    onToggle: (T) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        Text(text = title)
        options.forEachIndexed { index, option ->
            val checked = option in selected
            SegmentedListItem(
                checked = checked,
                onCheckedChange = { onToggle(option) },
                shapes = ListItemDefaults.segmentedShapes2(index, options.size),
                content = {
                    Text(text = label(option))
                },
                trailingContent = {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = { onToggle(option) },
                    )
                },
                modifier =
                    Modifier.clickable {
                        onToggle(option)
                    },
            )
        }
    }
}

@Composable
private fun filterKindLabel(kind: TimelinePostKind): String =
    when (kind) {
        TimelinePostKind.Reply -> stringResource(id = R.string.tab_settings_filter_reply)
        TimelinePostKind.Repost -> stringResource(id = R.string.tab_settings_filter_repost)
        TimelinePostKind.Quote -> stringResource(id = R.string.tab_settings_filter_quote)
        TimelinePostKind.Original -> error("Original is not exposed in timeline filter UI")
    }

@Composable
private fun filterContentLabel(content: TimelinePostContent): String =
    when (content) {
        TimelinePostContent.Text -> stringResource(id = R.string.tab_settings_filter_text_only)
        TimelinePostContent.Image -> stringResource(id = R.string.tab_settings_filter_image)
        TimelinePostContent.Video -> stringResource(id = R.string.tab_settings_filter_video)
        TimelinePostContent.Other -> error("Other is not exposed in timeline filter UI")
    }
