package dev.dimension.flare.ui.component.status

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.dimension.flare.compose.ui.Res
import dev.dimension.flare.compose.ui.share_replies_children
import dev.dimension.flare.compose.ui.share_replies_empty
import dev.dimension.flare.compose.ui.share_replies_failed
import dev.dimension.flare.compose.ui.share_replies_more
import dev.dimension.flare.compose.ui.share_replies_retry
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.HorizontalDivider
import dev.dimension.flare.ui.component.LocalTimelineAppearance
import dev.dimension.flare.ui.component.platform.PlatformButton
import dev.dimension.flare.ui.component.platform.PlatformCheckbox
import dev.dimension.flare.ui.component.platform.PlatformText
import dev.dimension.flare.ui.component.platform.PlatformTextButton
import dev.dimension.flare.ui.component.platform.PlatformTextStyle
import dev.dimension.flare.ui.model.UiTimelineV2
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
                replies.forEachIndexed { index, reply ->
                    if (index > 0) HorizontalDivider()
                    CompositionLocalProvider(
                        LocalTimelineAppearance provides LocalTimelineAppearance.current.copy(lineLimit = Int.MAX_VALUE),
                    ) {
                        StatusItem(item = reply)
                    }
                }
            }
        }
    }
}

@Composable
public fun StatusShareReplyList(
    state: StatusSharePresenter.State,
    modifier: Modifier = Modifier,
) {
    CompositionLocalProvider(PlatformTextStyle provides PlatformTheme.typography.body) {
        LazyColumn(modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 8.dp)) {
            itemsIndexed(state.replies, key = { _, reply -> reply.statusKey.toString() }) { index, reply ->
                if (index > 0) HorizontalDivider()
                Box {
                    Row(Modifier.focusProperties { onEnter = { cancelFocusChange() } }.focusGroup().clearAndSetSemantics { }) {
                        PlatformCheckbox(
                            checked = state.isSelected(reply.statusKey),
                            onCheckedChange = null,
                            modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                        )
                        StatusItem(item = reply, modifier = Modifier.weight(1f))
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
            }
            item {
                when {
                    state.loading -> {
                        StatusItem(item = null)
                    }

                    state.error != null -> {
                        Column {
                            PlatformText(stringResource(Res.string.share_replies_failed))
                            PlatformButton(onClick = state::loadMore) { PlatformText(stringResource(Res.string.share_replies_retry)) }
                        }
                    }

                    state.hasMore -> {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            PlatformButton(onClick = state::loadMore) { PlatformText(stringResource(Res.string.share_replies_more)) }
                        }
                    }

                    state.replies.isEmpty() -> {
                        PlatformText(stringResource(Res.string.share_replies_empty))
                    }
                }
            }
        }
    }
}
