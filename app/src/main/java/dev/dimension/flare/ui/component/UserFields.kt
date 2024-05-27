package dev.dimension.flare.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableMap
import moe.tlaster.ktml.dom.Element

@Composable
fun UserFields(
    fields: ImmutableMap<String, Element>,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
    ) {
        fields.onEachIndexed { index, (key, value) ->
            UserField(
                key = key,
                value = value,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            if (index != fields.size - 1) {
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun UserField(
    key: String,
    value: Element,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
    ) {
        Text(
            text = key,
            style = MaterialTheme.typography.bodySmall,
        )
        HtmlText(
            element = value,
        )
    }
}
