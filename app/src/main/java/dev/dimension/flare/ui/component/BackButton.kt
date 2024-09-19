package dev.dimension.flare.ui.component

import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType.Companion.NonZero
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap.Companion.Butt
import androidx.compose.ui.graphics.StrokeJoin.Companion.Miter
import androidx.compose.ui.graphics.vector.ImageVector.Builder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dimension.flare.R

@Composable
fun BackButton(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onBack, modifier = modifier) {
        FAIcon(
            imageVector = arrow,
            contentDescription = stringResource(id = R.string.navigate_back),
        )
    }
}

private val arrow by lazy {
    Builder(
        name = "Arrow",
        defaultWidth = 448.0.dp,
        defaultHeight = 512.0.dp,
        viewportWidth = 448.0f,
        viewportHeight = 512.0f,
        autoMirror = true,
    ).apply {
        path(
            fill = SolidColor(Color(0xFF000000)),
            stroke = null,
            strokeLineWidth = 0.0f,
            strokeLineCap = Butt,
            strokeLineJoin = Miter,
            strokeLineMiter = 4.0f,
            pathFillType = NonZero,
        ) {
            moveTo(9.4f, 233.4f)
            curveToRelative(-12.5f, 12.5f, -12.5f, 32.8f, 0.0f, 45.3f)
            lineToRelative(160.0f, 160.0f)
            curveToRelative(12.5f, 12.5f, 32.8f, 12.5f, 45.3f, 0.0f)
            reflectiveCurveToRelative(12.5f, -32.8f, 0.0f, -45.3f)
            lineTo(109.2f, 288.0f)
            lineTo(416.0f, 288.0f)
            curveToRelative(17.7f, 0.0f, 32.0f, -14.3f, 32.0f, -32.0f)
            reflectiveCurveToRelative(-14.3f, -32.0f, -32.0f, -32.0f)
            lineToRelative(-306.7f, 0.0f)
            lineTo(214.6f, 118.6f)
            curveToRelative(12.5f, -12.5f, 12.5f, -32.8f, 0.0f, -45.3f)
            reflectiveCurveToRelative(-32.8f, -12.5f, -45.3f, 0.0f)
            lineToRelative(-160.0f, 160.0f)
            close()
        }
    }.build()
}
