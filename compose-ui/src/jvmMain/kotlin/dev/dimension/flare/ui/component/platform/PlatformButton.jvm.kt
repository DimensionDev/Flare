package dev.dimension.flare.ui.component.platform

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import dev.dimension.flare.ui.theme.PlatformTheme
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.AccentButton
import io.github.composefluent.component.Button
import io.github.composefluent.component.ButtonColor
import io.github.composefluent.component.ButtonDefaults
import io.github.composefluent.component.SubtleButton

@Composable
internal actual fun PlatformButton(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        content = content,
        disabled = !enabled,
    )
}

@Composable
internal actual fun PlatformTextButton(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    content: @Composable RowScope.() -> Unit,
) {
    SubtleButton(
        onClick = onClick,
        modifier = modifier,
        content = content,
        disabled = !enabled,
    )
}

@Composable
internal actual fun PlatformFilledTonalButton(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    content: @Composable RowScope.() -> Unit,
) {
    AccentButton(
        onClick = onClick,
        modifier = modifier,
        content = content,
        disabled = !enabled,
    )
}

@Composable
internal actual fun PlatformOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        content = content,
        disabled = !enabled,
    )
}

@Composable
internal actual fun PlatformErrorButton(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    content: @Composable RowScope.() -> Unit,
) {
    val error = PlatformTheme.colorScheme.error
    AccentButton(
        onClick = onClick,
        modifier = modifier,
        content = content,
        disabled = !enabled,
        buttonColors =
            ButtonDefaults.accentButtonColors(
                default =
                    ButtonColor(
                        fillColor = error,
                        contentColor = Color.White,
                        borderBrush = FluentTheme.colors.borders.accentControl,
                    ),
                hovered =
                    ButtonColor(
                        fillColor = error.copy(alpha = 0.92f),
                        contentColor = Color.White,
                        borderBrush = FluentTheme.colors.borders.accentControl,
                    ),
                pressed =
                    ButtonColor(
                        fillColor = error.copy(alpha = 0.84f),
                        contentColor = Color.White,
                        borderBrush = FluentTheme.colors.borders.accentControl,
                    ),
                disabled =
                    ButtonColor(
                        fillColor = error.copy(alpha = 0.4f),
                        contentColor = Color.White.copy(alpha = 0.7f),
                        borderBrush = SolidColor(Color.Transparent),
                    ),
            ),
    )
}

@Composable
internal actual fun PlatformIconButton(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    content: @Composable () -> Unit,
) {
    SubtleButton(
        onClick = onClick,
        modifier = modifier,
        content = { content.invoke() },
        iconOnly = true,
        disabled = !enabled,
    )
}
