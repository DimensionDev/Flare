package dev.dimension.flare.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.dimension.flare.ui.theme.isLight
import kotlinx.collections.immutable.persistentMapOf

private data class ThemedIconColors(
    val contentColor: Color,
    val backgroundColor: Color,
)

private data class ThemedIconTheme(
    val dark: ThemedIconColors,
    val light: ThemedIconColors,
)

internal data class ThemeIconData private constructor(
    private val theme: ThemedIconTheme,
) {
    enum class Color {
        DeepTeal,
        SapphireBlue,
        ImperialMagenta,
        BurntUmber,
        DarkAmber,
        ForestGreen,
        RoyalPurple,
        CharcoalGrey,
    }
}

private val iconColorData =
    persistentMapOf(
        ThemeIconData.Color.DeepTeal to
            ThemedIconTheme(
                dark =
                    ThemedIconColors(
                        contentColor = Color(0xFF004e5d),
                        backgroundColor = Color(0xFF60d5f3),
                    ),
                light =
                    ThemedIconColors(
                        contentColor = Color(0xFF004e5d),
                        backgroundColor = Color(0xFFacedff),
                    ),
            ),
        ThemeIconData.Color.SapphireBlue to
            ThemedIconTheme(
                dark =
                    ThemedIconColors(
                        contentColor = Color(0xFF04409f),
                        backgroundColor = Color(0xFFa1c9ff),
                    ),
                light =
                    ThemedIconColors(
                        contentColor = Color(0xFF04409f),
                        backgroundColor = Color(0xFFd0e4ff),
                    ),
            ),
        ThemeIconData.Color.ImperialMagenta to
            ThemedIconTheme(
                dark =
                    ThemedIconColors(
                        contentColor = Color(0xFF8d0053),
                        backgroundColor = Color(0xFFffaee4),
                    ),
                light =
                    ThemedIconColors(
                        contentColor = Color(0xFF8d0053),
                        backgroundColor = Color(0xFFffd8ef),
                    ),
            ),
        ThemeIconData.Color.BurntUmber to
            ThemedIconTheme(
                dark =
                    ThemedIconColors(
                        contentColor = Color(0xFF753403),
                        backgroundColor = Color(0xFFffb683),
                    ),
                light =
                    ThemedIconColors(
                        contentColor = Color(0xFF753403),
                        backgroundColor = Color(0xFFffdcc3),
                    ),
            ),
        ThemeIconData.Color.DarkAmber to
            ThemedIconTheme(
                dark =
                    ThemedIconColors(
                        contentColor = Color(0xFF6d3a01),
                        backgroundColor = Color(0xFFfcbd00),
                    ),
                light =
                    ThemedIconColors(
                        contentColor = Color(0xFF6d3a01),
                        backgroundColor = Color(0xFFffe07c),
                    ),
            ),
        ThemeIconData.Color.ForestGreen to
            ThemedIconTheme(
                dark =
                    ThemedIconColors(
                        contentColor = Color(0xFF00522c),
                        backgroundColor = Color(0xFF80da88),
                    ),
                light =
                    ThemedIconColors(
                        contentColor = Color(0xFF00522c),
                        backgroundColor = Color(0xFFbeefbb),
                    ),
            ),
        ThemeIconData.Color.RoyalPurple to
            ThemedIconTheme(
                dark =
                    ThemedIconColors(
                        contentColor = Color(0xFF5629a4),
                        backgroundColor = Color(0xFFd9bafd),
                    ),
                light =
                    ThemedIconColors(
                        contentColor = Color(0xFF5629a4),
                        backgroundColor = Color(0xFFeedcfe),
                    ),
            ),
        ThemeIconData.Color.CharcoalGrey to
            ThemedIconTheme(
                dark =
                    ThemedIconColors(
                        contentColor = Color(0xFF474747),
                        backgroundColor = Color(0xFFc7c7c7),
                    ),
                light =
                    ThemedIconColors(
                        contentColor = Color(0xFF474747),
                        backgroundColor = Color(0xFFe3e3e3),
                    ),
            ),
    )

@Composable
internal fun ThemedIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    color: ThemeIconData.Color,
    modifier: Modifier = Modifier,
) {
    val isLight = MaterialTheme.colorScheme.isLight()
    val theme =
        remember(color) {
            iconColorData[color]?.let {
                if (isLight) {
                    it.light
                } else {
                    it.dark
                }
            } ?: error("Unknown color: $color")
        }
    Box(
        modifier =
            modifier
                .background(theme.backgroundColor, shape = CircleShape)
                .size(40.dp),
        contentAlignment = Alignment.Center,
    ) {
        FAIcon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = theme.contentColor,
        )
    }
}
