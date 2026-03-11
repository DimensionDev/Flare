package dev.dimension.flare.ui.screen.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.ArrowUpFromBracket
import compose.icons.fontawesomeicons.solid.ArrowsRotate
import compose.icons.fontawesomeicons.solid.BoxOpen
import compose.icons.fontawesomeicons.solid.Pen
import compose.icons.fontawesomeicons.solid.TriangleExclamation
import dev.dimension.flare.LocalWindowPadding
import dev.dimension.flare.Res
import dev.dimension.flare.draft_box_edit
import dev.dimension.flare.draft_box_empty
import dev.dimension.flare.draft_box_retry
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareScrollBar
import dev.dimension.flare.ui.component.NetworkImage
import dev.dimension.flare.ui.model.UiDraft
import dev.dimension.flare.ui.model.UiDraftMediaType
import dev.dimension.flare.ui.model.UiDraftStatus
import dev.dimension.flare.ui.model.primaryAccountKey
import dev.dimension.flare.ui.presenter.compose.DraftBoxPresenter
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.AccentButton
import io.github.composefluent.component.Button
import io.github.composefluent.component.Text
import io.github.composefluent.surface.Card
import moe.tlaster.precompose.molecule.producePresenter
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun DraftBoxScreen(onEdit: (String, MicroBlogKey) -> Unit = { _, _ -> }) {
    val state by producePresenter {
        remember { DraftBoxPresenter() }.invoke()
    }
    val listState = rememberLazyListState()
    FlareScrollBar(
        state = listState,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start = screenHorizontalPadding,
                    end = screenHorizontalPadding,
                    top = LocalWindowPadding.current.calculateTopPadding(),
                    bottom = LocalWindowPadding.current.calculateBottomPadding(),
                ),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (state.items.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillParentMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                    ) {
                        FAIcon(
                            FontAwesomeIcons.Solid.BoxOpen,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = FluentTheme.colors.text.text.secondary,
                        )
                        Text(
                            text = stringResource(Res.string.draft_box_empty),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                items(state.items, key = { it.groupId }) { item ->
                    DraftBoxCard(
                        item = item,
                        onRetry = { state.retry(item.groupId) },
                        onEdit = { item.primaryAccountKey?.let { accountKey -> onEdit(item.groupId, accountKey) } },
                    )
                }
            }
        }
    }
}

@Composable
private fun DraftBoxCard(
    item: UiDraft,
    onRetry: () -> Unit,
    onEdit: () -> Unit,
) {
    val disabled = item.status == UiDraftStatus.SENDING
    Card(
        onClick = onEdit,
        disabled = disabled,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item.accounts.forEach { account ->
                        AvatarComponent(
                            data = account.avatar,
                            size = 22.dp,
                        )
                    }
                }
                if (item.status == UiDraftStatus.FAILED) {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.TriangleExclamation,
                        contentDescription = null,
                        tint = FluentTheme.colors.system.critical,
                        modifier = Modifier.align(Alignment.TopEnd),
                    )
                } else if (item.status == UiDraftStatus.SENDING) {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.ArrowUpFromBracket,
                        contentDescription = null,
                        tint = FluentTheme.colors.system.success,
                        modifier = Modifier
                            .align(Alignment.TopEnd),
                    )
                }
            }

            item.data.spoilerText
                ?.takeIf { it.isNotBlank() }
                ?.let { spoilerText ->
                    Text(
                        text = spoilerText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = FluentTheme.colors.text.text.secondary,
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

            if (item.medias.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item.medias.take(4).forEach { media ->
                        when (media.type) {
                            UiDraftMediaType.IMAGE ->
                                NetworkImage(
                                    model = media.cachePath,
                                    contentDescription = null,
                                    modifier = Modifier.size(60.dp),
                                    contentScale = ContentScale.Crop,
                                )

                            UiDraftMediaType.VIDEO,
                            UiDraftMediaType.OTHER,
                            ->
                                Box(
                                    modifier =
                                        Modifier
                                            .size(60.dp)
                                            .alpha(0.75f),
                                ) {
                                    FAIcon(
                                        imageVector =
                                            if (media.type == UiDraftMediaType.VIDEO) {
                                                FontAwesomeIcons.Solid.ArrowsRotate
                                            } else {
                                                FontAwesomeIcons.Solid.Pen
                                            },
                                        contentDescription = null,
                                        modifier = Modifier.align(Alignment.Center),
                                    )
                                }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                if (item.status == UiDraftStatus.FAILED) {
                    AccentButton(onClick = onRetry) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            FAIcon(
                                imageVector = FontAwesomeIcons.Solid.ArrowsRotate,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(stringResource(Res.string.draft_box_retry))
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                if (item.status != UiDraftStatus.SENDING) {
                    Button(onClick = onEdit) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            FAIcon(
                                imageVector = FontAwesomeIcons.Solid.Pen,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(stringResource(Res.string.draft_box_edit))
                        }
                    }
                }
            }
        }
    }
}
