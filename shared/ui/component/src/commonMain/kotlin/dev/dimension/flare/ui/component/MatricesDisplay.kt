package dev.dimension.flare.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.dimension.flare.ui.component.platform.PlatformText
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.theme.PlatformTheme
import kotlinx.collections.immutable.persistentMapOf
import org.jetbrains.compose.resources.stringResource
import kotlin.collections.component1
import kotlin.collections.component2

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MatricesDisplay(
    data: UiProfile.Matrices,
    onClicked: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
) {
    val data =
        remember(data) {
            persistentMapOf(
                Res.string.profile_misskey_header_status_count to data.statusesCountHumanized,
                Res.string.profile_header_following_count to data.followsCountHumanized,
                Res.string.profile_header_fans_count to data.fansCountHumanized,
            )
        }
    if (expanded) {
        Row(
            modifier = modifier.wrapContentHeight(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            data.onEachIndexed { index, (resId, text) ->
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .clickable {
                                onClicked.invoke(index)
                            },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    PlatformText(
                        text = text,
                        style = PlatformTheme.typography.caption,
                    )
                    PlatformText(
                        text = stringResource(resId, 0).removePrefix("0 "),
                        style = PlatformTheme.typography.caption,
                    )
                }
                if (index != data.size - 1) {
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
            data.onEachIndexed { index, (resId, text) ->
                PlatformText(
                    text = stringResource(resId, text),
                    style = PlatformTheme.typography.caption,
                    modifier =
                        Modifier.clickable {
                            onClicked.invoke(index)
                        },
                )
            }
        }
    }
}
