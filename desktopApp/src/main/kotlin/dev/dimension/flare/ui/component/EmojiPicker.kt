package dev.dimension.flare.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.dimension.flare.Res
import dev.dimension.flare.emoji_picker_recent
import dev.dimension.flare.emoji_picker_search
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiEmoji
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.compose.EmojiHistoryPresenter
import dev.dimension.flare.ui.presenter.invoke
import io.github.composefluent.component.ListHeader
import io.github.composefluent.component.Text
import io.github.composefluent.component.TextField
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableList
import moe.tlaster.precompose.molecule.producePresenter
import org.jetbrains.compose.resources.stringResource

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
    var text by remember { mutableStateOf("") }
    val actualData =
        remember(data, text) {
            data.mapValues { (_, emojis) ->
                emojis.filter { emoji ->
                    emoji.searchKeywords.any { keyword ->
                        keyword.contains(text, ignoreCase = true)
                    }
                }
            }
        }
    val gridListState = rememberLazyGridState()
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text(stringResource(Res.string.emoji_picker_search)) },
            maxLines = 1,
            modifier = Modifier.fillMaxWidth(),
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(32.dp),
            state = gridListState,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.history.onSuccess { history ->
                if (text.isEmpty() && history.isNotEmpty()) {
                    item(
                        span = {
                            GridItemSpan(maxLineSpan)
                        },
                    ) {
                        ListHeader(
                            content = {
                                Text(stringResource(Res.string.emoji_picker_recent))
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
                                    .size(32.dp)
                                    .clickable {
                                        onEmojiSelected(emoji)
                                        state.addHistory(emoji)
                                    },
                        )
                    }
                }
//                item(
//                    span = {
//                        GridItemSpan(maxLineSpan)
//                    },
//                ) {
//                    HorizontalDivider()
//                }
            }

            actualData.forEach { (category, emojis) ->
                if (emojis.any()) {
                    if (category.isNotEmpty() && category.isNotBlank()) {
                        item(
                            span = {
                                GridItemSpan(maxLineSpan)
                            },
                        ) {
                            Text(category)
                        }
                    }

                    items(
                        emojis.size,
                    ) { index ->
                        val emoji = emojis[index]
                        NetworkImage(
                            model = emoji.url,
                            contentDescription = emoji.shortcode,
                            modifier =
                                Modifier
                                    .size(32.dp)
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
