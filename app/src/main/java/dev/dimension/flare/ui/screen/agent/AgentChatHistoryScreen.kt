package dev.dimension.flare.ui.screen.agent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Plus
import dev.dimension.flare.R
import dev.dimension.flare.feature.agent.presenter.AgentChatHistoryPresenter
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.DateTimeText
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareLargeFlexibleTopAppBar
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import dev.dimension.flare.ui.theme.segmentedShapes2
import moe.tlaster.precompose.molecule.producePresenter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AgentChatHistoryScreen(
    onBack: () -> Unit,
    onConversationClick: (String) -> Unit,
    onNewConversationClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by producePresenter {
        AgentChatHistoryPresenter().invoke()
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
        if (state.conversations.isEmpty()) {
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
                itemsIndexed(state.conversations, key = { _, item -> item.id }) { index, conversation ->
                    AgentHistoryConversationItem(
                        conversation = conversation,
                        index = index,
                        totalCount = state.conversations.size,
                        onClick = {
                            onConversationClick(conversation.id)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AgentHistoryConversationItem(
    conversation: AgentChatHistoryPresenter.Conversation,
    index: Int,
    totalCount: Int,
    onClick: () -> Unit,
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes2(index, totalCount),
        modifier = Modifier.fillMaxWidth(),
        content = {
            Row {
                Text(
                    text = conversation.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                DateTimeText(
                    data = conversation.updatedAt,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
    )
}
