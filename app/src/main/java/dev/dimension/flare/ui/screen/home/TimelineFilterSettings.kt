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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import dev.dimension.flare.data.model.tab.TimelineReplyVisibility
import dev.dimension.flare.data.model.tab.replyVisibility
import dev.dimension.flare.data.model.tab.withReplyVisibility
import dev.dimension.flare.ui.component.FlareDropdownMenu
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
    var selectedReplyVisibility by remember(filterConfig) {
        mutableStateOf(filterConfig.replyVisibility)
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
                    leadingItemCount = 1,
                    extraContent = {
                        ReplyVisibilitySelector(
                            selected = selectedReplyVisibility,
                            onSelect = { selectedReplyVisibility = it },
                            shapes = ListItemDefaults.segmentedShapes2(0, kindOptions.size + 1),
                        )
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
                        ).withReplyVisibility(selectedReplyVisibility),
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
    leadingItemCount: Int = 0,
    extraContent: @Composable () -> Unit = {},
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        Text(text = title)
        extraContent()
        options.forEachIndexed { index, option ->
            val checked = option in selected
            SegmentedListItem(
                checked = checked,
                onCheckedChange = { onToggle(option) },
                shapes = ListItemDefaults.segmentedShapes2(index + leadingItemCount, options.size + leadingItemCount),
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ReplyVisibilitySelector(
    selected: TimelineReplyVisibility,
    onSelect: (TimelineReplyVisibility) -> Unit,
    shapes: ListItemShapes,
) {
    var expanded by remember { mutableStateOf(false) }
    SegmentedListItem(
        onClick = { expanded = true },
        shapes = shapes,
        content = {
            Text(text = stringResource(id = R.string.tab_settings_filter_reply))
        },
        trailingContent = {
            TextButton(onClick = { expanded = true }) {
                Text(text = stringResource(id = selected.label))
            }
            FlareDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                TimelineReplyVisibility.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(text = stringResource(id = option.label)) },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        },
                    )
                }
            }
        },
    )
}

private val TimelineReplyVisibility.label: Int
    get() =
        when (this) {
            TimelineReplyVisibility.AllReplies -> R.string.tab_settings_filter_all_replies
            TimelineReplyVisibility.ToFollowedAccounts -> R.string.tab_settings_filter_to_followed_accounts
            TimelineReplyVisibility.NoReplies -> R.string.tab_settings_filter_no_replies
        }

@Composable
private fun filterKindLabel(kind: TimelinePostKind): String =
    when (kind) {
        TimelinePostKind.Reply, TimelinePostKind.ReplyToUnfollowed -> error("Replies use a dedicated filter")
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
