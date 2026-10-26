package dev.dimension.flare.ui.component.status

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.dimension.flare.compose.ui.Res
import dev.dimension.flare.compose.ui.share_replies_cancel
import dev.dimension.flare.compose.ui.share_replies_children
import dev.dimension.flare.compose.ui.share_replies_clear
import dev.dimension.flare.compose.ui.share_replies_done
import dev.dimension.flare.compose.ui.share_replies_empty
import dev.dimension.flare.compose.ui.share_replies_failed
import dev.dimension.flare.compose.ui.share_replies_more
import dev.dimension.flare.compose.ui.share_replies_partial
import dev.dimension.flare.compose.ui.share_replies_retry
import dev.dimension.flare.compose.ui.share_replies_select
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.HorizontalDivider
import dev.dimension.flare.ui.component.platform.PlatformButton
import dev.dimension.flare.ui.component.platform.PlatformCheckbox
import dev.dimension.flare.ui.component.platform.PlatformCircularProgressIndicator
import dev.dimension.flare.ui.component.platform.PlatformText
import dev.dimension.flare.ui.component.platform.PlatformTextButton
import dev.dimension.flare.ui.component.platform.PlatformTextStyle
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.contentPostOrNull
import dev.dimension.flare.ui.presenter.status.StatusSharePresenter
import dev.dimension.flare.ui.theme.PlatformTheme
import org.jetbrains.compose.resources.stringResource

@Composable
public fun StatusShareContent(
    status: UiTimelineV2?,
    statusKey: MicroBlogKey,
    replies: List<UiTimelineV2>,
) {
    CompositionLocalProvider(PlatformTextStyle provides PlatformTheme.typography.body) {
        Column {
            StatusItem(item = status, detailStatusKey = statusKey)
            if (replies.isNotEmpty()) {
                HorizontalDivider()
                PlatformText(stringResource(Res.string.share_replies_partial), Modifier.padding(16.dp))
                replies.forEach { reply ->
                    StatusItem(item = reply, detailStatusKey = reply.contentPostOrNull()?.statusKey ?: reply.statusKey)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
public fun StatusShareReplyButton(
    state: StatusSharePresenter.State,
    enabled: Boolean = true,
) {
    PlatformTextButton(onClick = state::selectReplies, enabled = enabled) {
        PlatformText(stringResource(Res.string.share_replies_select, state.selectedCount))
    }
}

@Composable
public fun StatusShareReplyPicker(
    state: StatusSharePresenter.State,
    modifier: Modifier = Modifier,
) {
    CompositionLocalProvider(PlatformTextStyle provides PlatformTheme.typography.body) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PlatformText(stringResource(Res.string.share_replies_select, state.selectedCount))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlatformTextButton(onClick = state::clearSelection) { PlatformText(stringResource(Res.string.share_replies_clear)) }
                PlatformTextButton(onClick = state::cancelSelection) { PlatformText(stringResource(Res.string.share_replies_cancel)) }
                PlatformButton(onClick = state::confirmSelection) { PlatformText(stringResource(Res.string.share_replies_done)) }
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                items(state.replies, key = { it.statusKey.toString() }) { reply ->
                    Box {
                        Row(Modifier.focusProperties { onEnter = { cancelFocusChange() } }.focusGroup().clearAndSetSemantics { }) {
                            PlatformCheckbox(checked = state.isSelected(reply.statusKey), onCheckedChange = null)
                            StatusItem(item = reply, detailStatusKey = reply.statusKey, modifier = Modifier.weight(1f))
                        }
                        Box(
                            Modifier
                                .matchParentSize()
                                .toggleable(
                                    value = state.isSelected(reply.statusKey),
                                    role = Role.Checkbox,
                                ) { state.toggleReply(reply.statusKey) }
                                .semantics { contentDescription = reply.searchText.orEmpty() },
                        )
                    }
                    if (reply.statusKey in state.expandableReplies) {
                        PlatformTextButton(onClick = { state.loadReplyChildren(reply.statusKey) }, enabled = !state.loading) {
                            PlatformText(stringResource(Res.string.share_replies_children))
                        }
                    }
                    HorizontalDivider()
                }
                item {
                    when {
                        state.loading -> {
                            PlatformCircularProgressIndicator(Modifier.padding(16.dp))
                        }

                        state.error != null -> {
                            Column {
                                PlatformText(stringResource(Res.string.share_replies_failed))
                                PlatformButton(onClick = state::loadMore) { PlatformText(stringResource(Res.string.share_replies_retry)) }
                            }
                        }

                        state.hasMore -> {
                            PlatformButton(onClick = state::loadMore) { PlatformText(stringResource(Res.string.share_replies_more)) }
                        }

                        state.replies.isEmpty() -> {
                            PlatformText(stringResource(Res.string.share_replies_empty))
                        }
                    }
                }
            }
        }
    }
}
