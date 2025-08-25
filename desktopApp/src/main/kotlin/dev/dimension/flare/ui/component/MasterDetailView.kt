package dev.dimension.flare.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.dimension.flare.ui.component.platform.isBigScreen
import io.github.composefluent.FluentTheme

@Composable
internal fun MasterDetailView(
    state: MasterDetailViewState,
    master: @Composable () -> Unit,
    detail: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (isBigScreen()) {
        Row(
            modifier =
                modifier
                    .background(
                        FluentTheme.colors.background.mica.base,
                    ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .width(320.dp)
                        .background(FluentTheme.colors.background.layer.default),
                content = { master() },
            )
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .background(FluentTheme.colors.background.layer.default),
                content = { detail() },
            )
        }
    } else {
        Box(
            modifier = modifier,
        ) {
            AnimatedContent(
                targetState = state,
            ) {
                when (it) {
                    MasterDetailViewState.Master -> master()
                    MasterDetailViewState.Detail -> detail()
                }
            }
        }
    }
}

internal enum class MasterDetailViewState {
    Master,
    Detail,
}
