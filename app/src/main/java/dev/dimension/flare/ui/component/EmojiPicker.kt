package dev.dimension.flare.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.CaretDown
import compose.icons.fontawesomeicons.solid.CaretUp
import dev.dimension.flare.R
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiEmoji
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.compose.EmojiHistoryPresenter
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.theme.segmentedShapes2
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableList
import moe.tlaster.precompose.molecule.producePresenter

@Composable
internal fun EmojiPicker(
    data: ImmutableMap<String, ImmutableList<UiEmoji>>,
    onEmojiSelected: (UiEmoji) -> Unit,
    accountType: AccountType,
    modifier: Modifier = Modifier,
) {
    val state by producePresenter(
        "EmojiPicker_$accountType",
    ) {
        presenter(
            accountType = accountType,
            data = data,
        )
    }
    val expandedCategory = remember { mutableStateListOf<String>() }
    val searchTextState = rememberTextFieldState()
    val actualData =
        remember(data, searchTextState.text) {
            data.mapValues { (_, emojis) ->
                emojis.filter { emoji ->
                    emoji.searchKeywords.any { keyword ->
                        keyword.contains(searchTextState.text, ignoreCase = true)
                    }
                }
            }
        }
    val gridListState = rememberLazyGridState()
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            state = searchTextState,
            label = { Text(stringResource(R.string.emoji_picker_search)) },
            lineLimits = TextFieldLineLimits.SingleLine,
            modifier = Modifier.fillMaxWidth(),
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(36.dp),
            state = gridListState,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            state.history.onSuccess { history ->
                if (searchTextState.text.isEmpty() && history.isNotEmpty()) {
                    item(
                        span = {
                            GridItemSpan(maxLineSpan)
                        },
                    ) {
                        ListItem(
                            colors =
                                ListItemDefaults.colors(
                                    containerColor = Color.Transparent,
                                ),
                            headlineContent = {
                                Text(stringResource(R.string.emoji_picker_recent))
                            },
                        )
                    }
                    items(
                        history.size,
                    ) { index ->
                        val emoji = history[index]
                        NetworkImage(
                            model = emoji.url,
                            contentDescription = emoji.shortcode,
                            modifier =
                                Modifier
                                    .size(36.dp)
                                    .clickable {
                                        onEmojiSelected(emoji)
                                        state.addHistory(emoji)
                                    },
                        )
                    }
                }
                item(
                    span = {
                        GridItemSpan(maxLineSpan)
                    },
                ) {
                    HorizontalDivider()
                }
            }

            actualData.onEachIndexed { index, (category, emojis) ->
                if (emojis.any()) {
                    if (actualData.size > 1) {
                        item(
                            span = {
                                GridItemSpan(maxLineSpan)
                            },
                        ) {
                            SegmentedListItem(
                                selected = expandedCategory.contains(category),
                                onClick = {
                                    if (expandedCategory.contains(category)) {
                                        expandedCategory.remove(category)
                                    } else {
                                        expandedCategory.add(category)
                                    }
                                },
                                shapes =
                                    ListItemDefaults.segmentedShapes2(
                                        index = index,
                                        count = data.size,
                                    ),
                                colors =
                                    ListItemDefaults.colors(
                                        containerColor = Color.Transparent,
                                    ),
                                content = {
                                    Text(category)
                                },
                                trailingContent = {
                                    FAIcon(
                                        if (expandedCategory.contains(category)) {
                                            FontAwesomeIcons.Solid.CaretUp
                                        } else {
                                            FontAwesomeIcons.Solid.CaretDown
                                        },
                                        contentDescription = null,
                                    )
                                },
                            )
                        }
                    }

                    if (expandedCategory.contains(category) || actualData.size == 1) {
                        items(
                            emojis.size,
                        ) { index ->
                            val emoji = emojis[index]
                            NetworkImage(
                                model = emoji.url,
                                contentDescription = emoji.shortcode,
                                modifier =
                                    Modifier
                                        .padding(2.dp)
                                        .size(36.dp)
                                        .clickable {
                                            onEmojiSelected(emoji)
                                            state.addHistory(emoji)
                                        },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun presenter(
    accountType: AccountType,
    data: ImmutableMap<String, ImmutableList<UiEmoji>>,
) = run {
    remember(accountType, data) {
        EmojiHistoryPresenter(
            accountType = accountType,
            emojis = data.values.flatten().toImmutableList(),
        )
    }.invoke()
}
