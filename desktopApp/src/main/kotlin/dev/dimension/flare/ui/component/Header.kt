package dev.dimension.flare.ui.component

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.Text

@Composable
fun Header(text: String) {
    Text(
        text = text,
        style = FluentTheme.typography.bodyStrong,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}
