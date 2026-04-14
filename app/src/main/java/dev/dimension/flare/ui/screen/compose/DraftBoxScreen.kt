package dev.dimension.flare.ui.screen.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.ArrowsRotate
import compose.icons.fontawesomeicons.solid.BoxOpen
import compose.icons.fontawesomeicons.solid.EllipsisVertical
import compose.icons.fontawesomeicons.solid.PaperPlane
import compose.icons.fontawesomeicons.solid.Pen
import compose.icons.fontawesomeicons.solid.Trash
import compose.icons.fontawesomeicons.solid.TriangleExclamation
import compose.icons.fontawesomeicons.solid.Upload
import compose.icons.fontawesomeicons.solid.Video
import dev.dimension.flare.R
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.DateTimeText
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareDropdownMenu
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.FlareTopAppBar
import dev.dimension.flare.ui.component.NetworkImage
import dev.dimension.flare.ui.model.UiDraft
import dev.dimension.flare.ui.model.UiDraftMediaType
import dev.dimension.flare.ui.model.UiDraftStatus
import dev.dimension.flare.ui.presenter.compose.DraftBoxPresenter
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import dev.dimension.flare.ui.theme.segmentedShapes2
import moe.tlaster.precompose.molecule.producePresenter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DraftBoxScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
) {
    val state by producePresenter {
        remember { DraftBoxPresenter() }.invoke()
    }
    FlareScaffold(
        topBar = {
            FlareTopAppBar(
                title = {
                    Text(text = stringResource(id = R.string.draft_box_title))
                },
                navigationIcon = {
                    BackButton(onBack = onBack)
                },
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = screenHorizontalPadding),
            contentPadding = paddingValues,
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            if (state.items.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillParentMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement =
                            Arrangement.spacedBy(
                                8.dp,
                                Alignment.CenterVertically,
                            ),
                    ) {
                        FAIcon(
                            imageVector = FontAwesomeIcons.Solid.BoxOpen,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(id = R.string.draft_box_empty),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                items(state.items, key = { item -> item.groupId }) { item ->
                    DraftBoxItem(
                        item = item,
                        index = state.items.indexOf(item),
                        total = state.items.size,
                        onRetry = { state.retry(item.groupId) },
                        onSend = { state.send(item.groupId) },
                        onDelete = { state.delete(item.groupId) },
                        onEdit = { onEdit(item.groupId) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DraftBoxItem(
    item: UiDraft,
    index: Int,
    total: Int,
    onRetry: () -> Unit,
    onSend: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showMenu by remember(item.groupId) { mutableStateOf(false) }
    SegmentedListItem(
        onClick = onEdit,
        modifier = modifier,
        shapes = ListItemDefaults.segmentedShapes2(index, total),
        enabled = item.status != UiDraftStatus.SENDING,
        leadingContent = {
            when (item.status) {
                UiDraftStatus.DRAFT -> {
                    Unit
                }

                UiDraftStatus.FAILED -> {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.TriangleExclamation,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                }

                UiDraftStatus.SENDING -> {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.Upload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        overlineContent = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                item.accounts.forEach { account ->
                    AvatarComponent(
                        data = account.avatar,
                        size = 22.dp,
                    )
                }
            }
        },
        content = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item.data.spoilerText
                    ?.takeIf { it.isNotBlank() }
                    ?.let { spoilerText ->
                        Text(
                            text = spoilerText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                item.data.content
                    .takeIf { it.isNotBlank() }
                    ?.let { text ->
                        Text(
                            text = text,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
            }
        },
        supportingContent = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (item.medias.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item.medias.take(4).forEach { media ->
                            when (media.type) {
                                UiDraftMediaType.IMAGE -> {
                                    NetworkImage(
                                        model = media.cachePath,
                                        contentDescription = null,
                                        modifier = Modifier.size(60.dp),
                                        contentScale = ContentScale.Crop,
                                    )
                                }

                                UiDraftMediaType.VIDEO,
                                UiDraftMediaType.OTHER,
                                -> {
                                    Box(
                                        modifier = Modifier.size(60.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        FAIcon(
                                            imageVector =
                                                if (media.type == UiDraftMediaType.VIDEO) {
                                                    FontAwesomeIcons.Solid.Video
                                                } else {
                                                    FontAwesomeIcons.Solid.Pen
                                                },
                                            contentDescription = null,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                DraftMetadataText(item)
            }
        },
        trailingContent = {
            if (item.status != UiDraftStatus.SENDING) {
                Box {
                    IconButton(
                        onClick = {
                            showMenu = true
                        },
                    ) {
                        FAIcon(
                            imageVector = FontAwesomeIcons.Solid.EllipsisVertical,
                            contentDescription = null,
                        )
                    }
                    FlareDropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = {
                            showMenu = false
                        },
                    ) {
                        if (item.status == UiDraftStatus.DRAFT) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(id = R.string.draft_box_send),
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onSend()
                                },
                                leadingIcon = {
                                    FAIcon(
                                        imageVector = FontAwesomeIcons.Solid.PaperPlane,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                },
                            )
                        }
                        if (item.status == UiDraftStatus.FAILED) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(id = R.string.draft_box_retry),
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onRetry()
                                },
                                leadingIcon = {
                                    FAIcon(
                                        imageVector = FontAwesomeIcons.Solid.ArrowsRotate,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = {
                                Text(text = stringResource(id = R.string.draft_box_edit))
                            },
                            onClick = {
                                showMenu = false
                                onEdit()
                            },
                            leadingIcon = {
                                FAIcon(
                                    imageVector = FontAwesomeIcons.Solid.Pen,
                                    contentDescription = null,
                                )
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(id = R.string.delete),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            },
                            onClick = {
                                showMenu = false
                                onDelete()
                            },
                            leadingIcon = {
                                FAIcon(
                                    imageVector = FontAwesomeIcons.Solid.Trash,
                                    contentDescription = null,
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

@Composable
private fun DraftMetadataText(item: UiDraft) {
    DateTimeText(
        data = item.updatedAt,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        fullTime = true,
    )
}
