package dev.dimension.flare.ui.component.platform

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.slapps.cupertino.LocalContentColor
import com.slapps.cupertino.ProvideTextStyle
import com.slapps.cupertino.theme.CupertinoTheme
import dev.dimension.flare.ui.component.status.ListComponent
import dev.dimension.flare.ui.theme.PlatformTheme

@Composable
internal actual fun PlatformListItem(
    headlineContent: @Composable (() -> Unit),
    modifier: Modifier,
    leadingContent: @Composable (() -> Unit),
    supportingContent: @Composable (() -> Unit),
    trailingContent: @Composable (() -> Unit),
) {
    ListComponent(
        headlineContent = {
            headlineContent.invoke()
        },
        modifier =
            modifier
                .background(PlatformTheme.colorScheme.card)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        leadingContent = {
            leadingContent.invoke()
        },
        supportingContent = {
            ProvideTextStyle(CupertinoTheme.typography.caption1) {
                CompositionLocalProvider(LocalContentColor provides CupertinoTheme.colorScheme.secondaryLabel) {
                    supportingContent.invoke()
                }
            }
        },
        trailingContent = {
            trailingContent.invoke()
        },
    )
}
