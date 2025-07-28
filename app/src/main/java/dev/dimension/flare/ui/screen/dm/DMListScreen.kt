package dev.dimension.flare.ui.screen.dm

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.CircleExclamation
import compose.icons.fontawesomeicons.solid.CircleUser
import compose.icons.fontawesomeicons.solid.List
import compose.icons.fontawesomeicons.solid.Message
import dev.dimension.flare.R
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.common.itemsIndexed
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.AvatarComponentDefaults
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareLargeFlexibleTopAppBar
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.ItemPlaceHolder
import dev.dimension.flare.ui.component.RefreshContainer
import dev.dimension.flare.ui.component.RichText
import dev.dimension.flare.ui.component.listCard
import dev.dimension.flare.ui.model.localizedShortTime
import dev.dimension.flare.ui.presenter.dm.DMListPresenter
import dev.dimension.flare.ui.presenter.dm.DMListState
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.theme.MediumAlpha
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter
import kotlin.math.min

@Composable
internal fun DMConversationDetailPlaceholder(modifier: Modifier = Modifier) {
    FlareScaffold(
        modifier = modifier,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(it),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, alignment = Alignment.CenterVertically),
        ) {
            FAIcon(
                FontAwesomeIcons.Solid.Message,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DMListScreen(
    accountType: AccountType,
    onBack: () -> Unit,
    onItemClicked: (MicroBlogKey) -> Unit,
) {
    val state by producePresenter("dm_list_$accountType") {
        presenter(accountType)
    }
    val topAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    FlareScaffold(
        modifier = Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
        topBar = {
            FlareLargeFlexibleTopAppBar(
                title = {
                    Text(text = stringResource(id = R.string.dm_list_title))
                },
                scrollBehavior = topAppBarScrollBehavior,
                navigationIcon = {
                    BackButton(onBack)
                },
            )
        },
    ) { contentPadding ->
        RefreshContainer(
            modifier =
                Modifier
                    .fillMaxSize(),
            indicatorPadding = contentPadding,
            isRefreshing = state.isRefreshing,
            onRefresh = state::refresh,
            content = {
                LazyColumn(
                    contentPadding = contentPadding,
                    modifier =
                        Modifier
                            .padding(horizontal = screenHorizontalPadding),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    itemsIndexed(
                        state.items,
                        emptyContent = {
                            Box(
                                modifier = Modifier.fillParentMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    FAIcon(
                                        imageVector = FontAwesomeIcons.Solid.List,
                                        contentDescription = stringResource(id = R.string.dm_list_empty),
                                        modifier = Modifier.size(48.dp),
                                    )
                                    Text(
                                        text = stringResource(id = R.string.dm_list_empty),
                                        style = MaterialTheme.typography.headlineMedium,
                                    )
                                }
                            }
                        },
                        loadingContent = { index, itemCount ->
                            ItemPlaceHolder(
                                modifier =
                                    Modifier
                                        .listCard(
                                            index = index,
                                            totalCount = itemCount,
                                        ),
                            )
                        },
                        errorContent = {
                            Box(
                                modifier = Modifier.fillParentMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    FAIcon(
                                        imageVector = FontAwesomeIcons.Solid.CircleExclamation,
                                        contentDescription = stringResource(id = R.string.dm_list_error),
                                        modifier = Modifier.size(48.dp),
                                    )
                                    Text(
                                        text = stringResource(id = R.string.dm_list_error),
                                        style = MaterialTheme.typography.headlineMedium,
                                    )
                                    Text(
                                        text = it.message.orEmpty(),
                                        style = MaterialTheme.typography.headlineMedium,
                                    )
                                }
                            }
                        },
                        itemContent = { index, itemCount, item ->
                            ListItem(
                                headlineContent = {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        if (item.hasUser) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f),
                                            ) {
                                                item.users.forEach { user ->
                                                    RichText(
                                                        text = user.name,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                    if (item.users.size == 1) {
                                                        Text(
                                                            text = user.handle,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            modifier =
                                                                Modifier
                                                                    .alpha(MediumAlpha),
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        val lastMessage = item.lastMessage
                                        if (lastMessage != null) {
                                            Text(
                                                text = lastMessage.timestamp.shortTime.localizedShortTime,
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier =
                                                    Modifier
                                                        .alpha(MediumAlpha),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                },
                                leadingContent = {
                                    if (!item.hasUser) {
                                        FAIcon(
                                            FontAwesomeIcons.Solid.CircleUser,
                                            contentDescription = null,
                                            modifier =
                                                Modifier
                                                    .size(AvatarComponentDefaults.size),
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    } else {
                                        Box(
                                            modifier =
                                                Modifier
                                                    .size(AvatarComponentDefaults.size),
                                        ) {
                                            repeat(
                                                min(item.users.size, 2),
                                            ) {
                                                val avatar = item.users[it].avatar
                                                if (item.users.size == 1) {
                                                    AvatarComponent(avatar)
                                                } else {
                                                    Box(
                                                        modifier =
                                                            Modifier
                                                                .offset(
                                                                    x = (it * 12).dp,
                                                                    y = (it * 12).dp,
                                                                ),
                                                    ) {
                                                        AvatarComponent(
                                                            avatar,
                                                            size = AvatarComponentDefaults.compatSize,
                                                        )
                                                    }
                                                }
                                            }
                                            if (item.users.size > 1) {
                                                Text(
                                                    item.users.size.toString(),
                                                    modifier =
                                                        Modifier
                                                            .align(Alignment.BottomEnd)
                                                            .background(
                                                                MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp),
                                                                shape = MaterialTheme.shapes.small,
                                                            ).padding(horizontal = 4.dp),
                                                )
                                            }
                                        }
                                    }
                                },
                                supportingContent = {
                                    Text(
                                        text = item.lastMessageText,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                trailingContent = {
                                    if (item.unreadCount > 0) {
                                        Badge {
                                            Text(
                                                text = item.unreadCount.toString(),
                                            )
                                        }
                                    }
                                },
                                modifier =
                                    Modifier
                                        .listCard(
                                            index = index,
                                            totalCount = itemCount,
                                        ).clickable {
                                            onItemClicked.invoke(item.key)
                                        },
                            )
                        },
                    )
                }
            },
        )
    }
}

@Composable
private fun presenter(accountType: AccountType) =
    run {
        val scope = rememberCoroutineScope()
        val state =
            remember(accountType) {
                DMListPresenter(accountType)
            }.invoke()
        object : DMListState by state {
            fun refresh() {
                scope.launch {
                    state.refreshSuspend()
                }
            }
        }
    }
