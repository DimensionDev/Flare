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
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dimension.flare.R
import dev.dimension.flare.ui.model.UiEmoji
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap

@Composable
internal fun EmojiPicker(
    data: ImmutableMap<String, ImmutableList<UiEmoji>>,
    onEmojiSelected: (UiEmoji) -> Unit,
    modifier: Modifier = Modifier,
) {
    val searchTextState = rememberTextFieldState()
    val actualData =
        remember(data, searchTextState.text) {
            data.mapValues { (_, emojis) ->
                emojis.filter { emoji ->
                    emoji.shortcode.contains(searchTextState.text, ignoreCase = true)
                }
            }
        }
    val gridListState = rememberLazyGridState()
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField2(
            state = searchTextState,
            label = { Text(stringResource(R.string.edit_list_member_search)) },
            modifier = Modifier.fillMaxWidth(),
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(48.dp),
            state = gridListState,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            actualData.forEach { (category, emojis) ->
                if (emojis.any()) {
                    // TODO: after compose 1.8.0, we can use `stickyHeader` to make the header sticky
                    if (category.isNotEmpty() && category.isNotBlank()) {
                        item(
                            span = {
                                GridItemSpan(maxLineSpan)
                            },
                            key = category,
                        ) {
                            ListItem(
                                colors =
                                    ListItemDefaults.colors(
                                        containerColor = Color.Transparent,
                                    ),
                                headlineContent = {
                                    Text(category)
                                },
                            )
                        }
                    }

                    items(
                        emojis.size,
                        key = { index -> emojis[index].shortcode },
                    ) { index ->
                        val emoji = emojis[index]
                        NetworkImage(
                            model = emoji.url,
                            contentDescription = emoji.shortcode,
                            contentScale = ContentScale.Fit,
                            modifier =
                                Modifier
                                    .size(48.dp)
                                    .clickable {
                                        onEmojiSelected(emoji)
                                    },
                        )
                    }
                }
            }
        }
    }
}
