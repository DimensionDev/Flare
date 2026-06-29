package dev.dimension.flare.ui.screen.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Plus
import compose.icons.fontawesomeicons.solid.Trash
import dev.dimension.flare.R
import dev.dimension.flare.feature.agent.common.AgentChatRoom
import dev.dimension.flare.feature.agent.presenter.AgentChatHistoryPresenter
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.DateTimeText
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareLargeFlexibleTopAppBar
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import dev.dimension.flare.ui.theme.segmentedShapes2
import moe.tlaster.precompose.molecule.producePresenter
import kotlin.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AgentChatHistoryScreen(
    onBack: () -> Unit,
    onConversationClick: (String) -> Unit,
    onNewConversationClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by producePresenter {
        remember { AgentChatHistoryPresenter() }.invoke()
    }
    val topAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    FlareScaffold(
        topBar = {
            FlareLargeFlexibleTopAppBar(
                title = {
                    Text(text = stringResource(id = R.string.agent_history_title))
                },
                navigationIcon = {
                    BackButton(onBack = onBack)
                },
                scrollBehavior = topAppBarScrollBehavior,
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewConversationClick) {
                FAIcon(
                    imageVector = FontAwesomeIcons.Solid.Plus,
                    contentDescription = stringResource(id = R.string.agent_chat_title),
                )
            }
        },
        modifier = modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
    ) { contentPadding ->
        if (state.rooms.isEmpty()) {
            Text(
                text = stringResource(id = R.string.agent_history_empty),
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(contentPadding)
                        .padding(24.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = screenHorizontalPadding),
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                itemsIndexed(state.rooms, key = { _, item -> item.id }) { index, room ->
                    AgentHistoryConversationItem(
                        room = room,
                        index = index,
                        totalCount = state.rooms.size,
                        onClick = {
                            onConversationClick(room.id)
                        },
                        onDelete = {
                            state.delete(room.id)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AgentHistoryConversationItem(
    room: AgentChatRoom,
    index: Int,
    totalCount: Int,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val shapes = ListItemDefaults.segmentedShapes2(index, totalCount)
    val swipeState = rememberSwipeToDismissBoxState()
    var showMenu by remember(room.id) { mutableStateOf(false) }

    LaunchedEffect(swipeState.settledValue) {
        if (swipeState.settledValue != SwipeToDismissBoxValue.Settled) {
            onDelete()
        }
    }

    Box {
        SwipeToDismissBox(
            state = swipeState,
            backgroundContent = {
                if (swipeState.dismissDirection != SwipeToDismissBoxValue.Settled) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(
                                    color = MaterialTheme.colorScheme.error,
                                    shape = shapes.draggedShape,
                                ).padding(16.dp),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        FAIcon(
                            imageVector = FontAwesomeIcons.Solid.Trash,
                            contentDescription = stringResource(id = R.string.delete),
                            tint = MaterialTheme.colorScheme.onError,
                        )
                    }
                }
            },
            enableDismissFromStartToEnd = false,
            enableDismissFromEndToStart = true,
        ) {
            SegmentedListItem(
                onClick = onClick,
                onLongClick = {
                    showMenu = true
                },
                onLongClickLabel = stringResource(id = R.string.more),
                shapes = shapes,
                modifier =
                    Modifier.fillMaxWidth(),
                content = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = room.title,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (room.isRunning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    strokeWidth = 2.dp,
                                )
                            }
                            DateTimeText(
                                data = Instant.fromEpochMilliseconds(room.updatedAt).toUi(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
            )
        }
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = {
                showMenu = false
            },
        ) {
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
