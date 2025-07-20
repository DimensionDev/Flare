package dev.dimension.flare.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.dimension.flare.ui.component.platform.PlatformText
import dev.dimension.flare.ui.render.UiRichText
import dev.dimension.flare.ui.theme.PlatformTheme
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import kotlinx.collections.immutable.ImmutableMap

@Composable
internal fun ColumnScope.UserFields(
    fields: ImmutableMap<String, UiRichText>,
) {
    fields.onEachIndexed { index, (key, value) ->
        UserField(
            key = key,
            value = value,
        )
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
