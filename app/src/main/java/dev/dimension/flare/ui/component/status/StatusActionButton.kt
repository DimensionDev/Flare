package dev.dimension.flare.ui.component.status

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.dimension.flare.data.model.LocalAppearanceSettings

@Composable
internal fun StatusActionButton(
    icon: ImageVector,
    text: String?,
    onClicked: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
    contentDescription: String? = null,
    enabled: Boolean = true,
    withTextMinWidth: Boolean = false,
    content: @Composable RowScope.() -> Unit = {},
) {
    val interactionSource = remember { MutableInteractionSource() }
    val appearanceSettings = LocalAppearanceSettings.current
    val textMinWidth =
        if (withTextMinWidth) {
            with(LocalDensity.current) { LocalTextStyle.current.fontSize.toDp() * 3 }
        } else {
            0.dp
        }
    Row(
        modifier =
            modifier
                .padding(vertical = 4.dp, horizontal = 8.dp)
                .height(with(LocalDensity.current) { LocalTextStyle.current.fontSize.toDp() + 4.dp }),
        verticalAlignment = Alignment.Bottom,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier =
                Modifier
                    .clickable(
                        onClick = onClicked,
                        enabled = enabled,
                        interactionSource = interactionSource,
                        indication =
                            ripple(
                                bounded = false,
                                radius = 20.dp,
                            ),
                    ),
            tint = color,
        )
        if (!text.isNullOrEmpty() && appearanceSettings.showNumbers) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = text,
//                style = MaterialTheme.typography.bodySmall,
                color = color,
                modifier = Modifier.width(textMinWidth),
            )
        } else {
            if (withTextMinWidth) {
                Spacer(modifier = Modifier.width(4.dp))
            }
            Box(
                modifier = Modifier.width(textMinWidth),
            )
        }
        content.invoke(this)
    }
}

@Composable
internal fun StatusActionGroup(
    icon: ImageVector,
    text: String?,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
    contentDescription: String? = null,
    enabled: Boolean = true,
    withTextMinWidth: Boolean = false,
    subMenus: @Composable ColumnScope.(closeMenu: () -> Unit) -> Unit = {},
) {
    var showMenu by remember { mutableStateOf(false) }
    StatusActionButton(
        icon = icon,
        text = text,
        modifier = modifier,
        contentDescription = contentDescription,
        onClicked = {
            showMenu = true
        },
        color = color,
        enabled = enabled,
        withTextMinWidth = withTextMinWidth,
        content = {
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
            ) {
                subMenus.invoke(this) {
                    showMenu = false
                }
            }
        },
    )
}
