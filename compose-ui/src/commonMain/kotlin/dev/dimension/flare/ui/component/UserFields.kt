package dev.dimension.flare.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.dimension.flare.ui.component.platform.PlatformText
import dev.dimension.flare.ui.render.UiRichText
import dev.dimension.flare.ui.theme.PlatformTheme
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import kotlinx.collections.immutable.ImmutableMap

@Composable
internal fun UserFields(
    fields: ImmutableMap<String, UiRichText>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        fields.onEachIndexed { index, (key, value) ->
            UserField(
                key = key,
                value = value,
                modifier = Modifier.padding(horizontal = screenHorizontalPadding),
            )
            if (index != fields.size - 1) {
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun UserField(
    key: String,
    value: UiRichText,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
    ) {
        PlatformText(
            text = key,
            style = PlatformTheme.typography.caption,
        )
        RichText(
            text = value,
        )
    }
}
