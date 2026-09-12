package dev.dimension.flare.ui.component.status

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.dimension.flare.compose.ui.Res
import dev.dimension.flare.compose.ui.delete
import dev.dimension.flare.compose.ui.outbox_edit
import dev.dimension.flare.compose.ui.outbox_progress
import dev.dimension.flare.compose.ui.outbox_status_failed
import dev.dimension.flare.compose.ui.outbox_status_sending
import dev.dimension.flare.compose.ui.outbox_status_sent
import dev.dimension.flare.compose.ui.status_loadmore_error_retry
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.DateTimeText
import dev.dimension.flare.ui.component.NetworkImage
import dev.dimension.flare.ui.component.platform.PlatformButton
import dev.dimension.flare.ui.component.platform.PlatformErrorButton
import dev.dimension.flare.ui.component.platform.PlatformFilledTonalButton
import dev.dimension.flare.ui.component.platform.PlatformLinearProgressIndicator
import dev.dimension.flare.ui.component.platform.PlatformText
import dev.dimension.flare.ui.model.UiDraftMediaType
import dev.dimension.flare.ui.model.UiOutboxPost
import dev.dimension.flare.ui.model.UiOutboxStatus
import dev.dimension.flare.ui.model.UiOutboxTarget
import dev.dimension.flare.ui.theme.PlatformTheme
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.stringResource

public fun LazyStaggeredGridScope.outboxItems(
    posts: ImmutableList<UiOutboxPost>,
    onRetry: (String) -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    items(
        count = posts.size,
        key = { index -> posts[index].key },
        contentType = { "outbox" },
        span = { StaggeredGridItemSpan.FullLine },
    ) { index ->
        val item = posts[index]
        AdaptiveCard(
            modifier = Modifier.fillMaxWidth(),
            respectTimelineMode = true,
        ) {
            OutboxPostItem(
                item = item,
                onRetry = { onRetry(item.groupId) },
                onEdit = { onEdit(item.groupId) },
                onDelete = { onDelete(item.groupId) },
            )
        }
    }
}

@Composable
private fun OutboxPostItem(
    item: UiOutboxPost,
    onRetry: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val inactive = item.status != UiOutboxStatus.FAILED
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(if (inactive) Modifier.alpha(0.62f).semantics { disabled() } else Modifier)
                .padding(horizontal = screenHorizontalPadding, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutboxHeader(item)

        item.data.spoilerText
            ?.takeIf { it.isNotBlank() }
            ?.let {
                PlatformText(
                    text = it,
                    color = PlatformTheme.colorScheme.caption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        item.data.content
            .takeIf { it.isNotBlank() }
            ?.let {
                PlatformText(
                    text = it,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                )
            }

        if (item.medias.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item.medias.take(4).forEach { media ->
                    if (media.type == UiDraftMediaType.IMAGE) {
                        NetworkImage(
                            model = media.cachePath,
                            contentDescription = media.altText,
                            modifier =
                                Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Box(
                            modifier =
                                Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(PlatformTheme.colorScheme.cardAlt),
                            contentAlignment = Alignment.Center,
                        ) {
                            PlatformText(
                                text = media.fileName.orEmpty(),
                                color = PlatformTheme.colorScheme.caption,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }

        OutboxProgress(item)

        if (item.targets.size > 1) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item.targets.forEach { target ->
                    OutboxTargetRow(target)
                }
            }
        }

        if (item.status == UiOutboxStatus.FAILED) {
            item.targets
                .firstNotNullOfOrNull { target -> target.errorMessage?.takeIf { it.isNotBlank() } }
                ?.let {
                    PlatformText(
                        text = it,
                        color = PlatformTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlatformButton(onClick = onEdit) {
                    PlatformText(stringResource(Res.string.outbox_edit))
                }
                PlatformErrorButton(onClick = onDelete) {
                    PlatformText(stringResource(Res.string.delete))
                }
                PlatformFilledTonalButton(onClick = onRetry) {
                    PlatformText(stringResource(Res.string.status_loadmore_error_retry))
                }
            }
        }
    }
}

@Composable
private fun OutboxHeader(item: UiOutboxPost) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            item.targets.take(4).forEach { target ->
                AvatarComponent(data = target.avatar, size = 24.dp)
            }
            if (item.targets.size == 1) {
                PlatformText(
                    text =
                        item.targets
                            .single()
                            .account
                            .accountKey
                            .toString(),
                    color = PlatformTheme.colorScheme.caption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        DateTimeText(
            data = item.updatedAt,
            color = PlatformTheme.colorScheme.caption,
            maxLines = 1,
        )
        PlatformText(
            text = statusLabel(item.status),
            color = statusColor(item.status),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun OutboxProgress(item: UiOutboxPost) {
    val progress = item.progressCurrent.toFloat() / item.progressMax.coerceAtLeast(1)
    PlatformLinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier =
            Modifier
                .fillMaxWidth()
                .height(4.dp),
        color = statusColor(item.status),
    )
    PlatformText(
        text = stringResource(Res.string.outbox_progress, item.progressCurrent, item.progressMax),
        color = PlatformTheme.colorScheme.caption,
    )
}

@Composable
private fun OutboxTargetRow(target: UiOutboxTarget) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarComponent(data = target.avatar, size = 20.dp)
        PlatformText(
            text = target.account.accountKey.toString(),
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        PlatformText(
            text = statusLabel(target.status),
            color = statusColor(target.status),
            maxLines = 1,
        )
        PlatformText(
            text = "${target.progressCurrent}/${target.progressMax}",
            color = PlatformTheme.colorScheme.caption,
            maxLines = 1,
        )
    }
}

@Composable
private fun statusLabel(status: UiOutboxStatus): String =
    stringResource(
        when (status) {
            UiOutboxStatus.SENDING -> Res.string.outbox_status_sending
            UiOutboxStatus.FAILED -> Res.string.outbox_status_failed
            UiOutboxStatus.SENT -> Res.string.outbox_status_sent
        },
    )

@Composable
private fun statusColor(status: UiOutboxStatus) =
    when (status) {
        UiOutboxStatus.SENDING -> PlatformTheme.colorScheme.primary
        UiOutboxStatus.FAILED -> PlatformTheme.colorScheme.error
        UiOutboxStatus.SENT -> PlatformTheme.colorScheme.retweetColor
    }
