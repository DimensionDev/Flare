package dev.dimension.flare.ui.component.status

import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
    content: @Composable RowScope.() -> Unit = {},
) {
    val interactionSource = remember { MutableInteractionSource() }
    val appearanceSettings = LocalAppearanceSettings.current
    Row(
        modifier =
            modifier
                .clickable(
                    indication = null,
                    interactionSource = interactionSource,
                    onClick = onClicked,
                    enabled = enabled,
                )
                .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier =
                Modifier
                    .indication(
                        interactionSource = interactionSource,
                        indication =
                            rememberRipple(
                                bounded = false,
                                radius = 20.dp,
                            ),
                    )
                    .size(16.dp),
            tint = color,
        )
        if (!text.isNullOrEmpty() && appearanceSettings.showNumbers) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = color,
            )
        }
        content.invoke(this)
    }
}

@Composable
internal fun StatusActionGroupComponent(
    action: StatusAction.Group,
    modifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }

    StatusActionButton(
        icon = action.icon,
        text = action.text,
        modifier = modifier,
        onClicked = {
            showMenu = true
        },
        color = action.color?.toColor() ?: LocalContentColor.current,
        enabled = action.enabled,
        content = {
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
            ) {
                action.items.forEach { item ->
                    DropdownMenuItem(
                        onClick = {
                            item.onClick.invoke()
                            showMenu = false
                        },
                        text = {
                            if (item.text != null) {
                                Text(text = item.text)
                            }
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                            )
                        },
                    )
                }
            }
        },
    )
}
