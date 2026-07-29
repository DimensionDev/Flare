package dev.dimension.flare.ui.plugin.badge

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.UiComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.dimension.flare.ui.FlareRenderer
import dev.dimension.flare.ui.compose.AbstractAndroidComposeWidget

@FlareRenderer
internal class AndroidComposeBadgeWidget :
    AbstractAndroidComposeWidget(),
    BadgeWidget {
    private var currentText: String by mutableStateOf("")
    private var currentTone: BadgeTone by mutableStateOf(BadgeTone.Neutral)
    private var clickAction: () -> Unit = {}
    private val performClick: () -> Unit = { clickAction() }

    override fun setText(value: String) {
        currentText = value
    }

    override fun setTone(value: BadgeTone) {
        currentTone = value
    }

    override fun setOnClick(value: () -> Unit) {
        clickAction = value
    }

    @Composable
    @UiComposable
    override fun Render() {
        BasicText(
            text = currentText,
            modifier =
                composeModifier
                    .background(
                        color = currentTone.backgroundColor(),
                        shape = RoundedCornerShape(BADGE_CORNER_RADIUS),
                    ).clickable(
                        role = Role.Button,
                        onClick = performClick,
                    ).padding(
                        horizontal = BADGE_HORIZONTAL_PADDING,
                        vertical = BADGE_VERTICAL_PADDING,
                    ),
        )
    }

    override fun dispose() {
        clickAction = {}
    }
}

private fun BadgeTone.backgroundColor(): Color =
    when (this) {
        BadgeTone.Neutral -> Color(0xFFE6E6E6)
        BadgeTone.Positive -> Color(0xFFBEEBC6)
        BadgeTone.Warning -> Color(0xFFFFE19B)
    }

private val BADGE_CORNER_RADIUS = 12.dp
private val BADGE_HORIZONTAL_PADDING = 10.dp
private val BADGE_VERTICAL_PADDING = 4.dp
