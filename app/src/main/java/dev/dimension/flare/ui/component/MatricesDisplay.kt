package dev.dimension.flare.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableMap

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MatricesDisplay(
    matrices: ImmutableMap<Int, String>,
    onClicked: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
) {
    if (expanded) {
        Row(
            modifier = modifier.wrapContentHeight(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            matrices.onEachIndexed { index, (resId, text) ->
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .clickable {
                                onClicked.invoke(index)
                            },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(resId, 0).removePrefix("0 "),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (index != matrices.size - 1) {
                    VerticalDivider()
                }
            }
        }
    } else {
        FlowRow(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            matrices.onEachIndexed { index, (resId, text) ->
                Text(
                    text = stringResource(resId, text),
                    style = MaterialTheme.typography.bodySmall,
                    modifier =
                        Modifier.clickable {
                            onClicked.invoke(index)
                        },
                )
            }
        }
    }
}
