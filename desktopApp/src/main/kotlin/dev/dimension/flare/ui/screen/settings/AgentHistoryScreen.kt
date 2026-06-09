package dev.dimension.flare.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Plus
import dev.dimension.flare.LocalWindowPadding
import dev.dimension.flare.Res
import dev.dimension.flare.agent_chat_title
import dev.dimension.flare.agent_history_empty
import dev.dimension.flare.feature.agent.common.AgentChatRoom
import dev.dimension.flare.feature.agent.presenter.AgentChatHistoryPresenter
import dev.dimension.flare.ui.component.DateTimeText
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareScrollBar
import dev.dimension.flare.ui.component.listCard
import dev.dimension.flare.ui.component.status.ListComponent
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.AccentButton
import io.github.composefluent.component.Text
import moe.tlaster.precompose.molecule.producePresenter
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

@Composable
internal fun AgentHistoryScreen(
    onConversationClick: (String) -> Unit,
    onNewConversationClick: () -> Unit,
) {
    val state by producePresenter {
        AgentChatHistoryPresenter().invoke()
    }
    val listState = rememberLazyListState()
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(LocalWindowPadding.current),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            AccentButton(onClick = onNewConversationClick) {
                FAIcon(
                    imageVector = FontAwesomeIcons.Solid.Plus,
                    contentDescription = stringResource(Res.string.agent_chat_title),
                )
            }
        }
        if (state.rooms.isEmpty()) {
            Text(
                text = stringResource(Res.string.agent_history_empty),
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                color = FluentTheme.colors.text.text.secondary,
            )
        } else {
            FlareScrollBar(listState) {
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = screenHorizontalPadding),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    state = listState,
                ) {
                    itemsIndexed(state.rooms, key = { _, item -> item.id }) { index, room ->
                        AgentHistoryConversationItem(
                            room = room,
                            index = index,
                            totalCount = state.rooms.size,
                            onClick = {
                                onConversationClick(room.id)
                            },
                        )
                    }
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
) {
    ListComponent(
        modifier =
            Modifier
                .fillMaxWidth()
                .listCard(
                    index = index,
                    totalCount = totalCount,
                ).background(FluentTheme.colors.control.default)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clickable(onClick = onClick),
        headlineContent = {
            Row {
                Text(
                    text = room.title,
                    style = FluentTheme.typography.body,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                DateTimeText(
                    data = Instant.fromEpochMilliseconds(room.updatedAt).toUi(),
                    style = FluentTheme.typography.caption,
                    color = FluentTheme.colors.text.text.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
    )
}
