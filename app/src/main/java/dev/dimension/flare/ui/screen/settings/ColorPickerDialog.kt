package dev.dimension.flare.ui.screen.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.skydoves.colorpicker.compose.AlphaTile
import com.github.skydoves.colorpicker.compose.BrightnessSlider
import com.github.skydoves.colorpicker.compose.ColorEnvelope
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import com.github.skydoves.colorpicker.compose.rememberColorPickerController
import dev.dimension.flare.R
import dev.dimension.flare.data.model.appearance.AppearanceKeys
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.ui.component.LocalGlobalAppearance
import dev.dimension.flare.ui.component.LocalTimelineAppearance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter
import org.koin.compose.koinInject

@Composable
internal fun ColorPickerDialog(onBack: () -> Unit) {
    val globalAppearance = LocalGlobalAppearance.current
    val timelineAppearance = LocalTimelineAppearance.current
    val state by producePresenter {
        presenter(initialColor = globalAppearance.colorSeed)
    }
    val controller = rememberColorPickerController()
    LaunchedEffect(Unit) {
        controller.selectByColor(Color(globalAppearance.colorSeed), fromUser = true)
    }

    AlertDialog(
        onDismissRequest = onBack,
        confirmButton = {
            TextButton(
                onClick = {
                    state.confirm()
                    onBack.invoke()
                },
            ) {
                Text(stringResource(id = android.R.string.ok))
            }
        },
        text = {
            Column(
                verticalArrangement =
                    androidx.compose.foundation.layout.Arrangement
                        .spacedBy(16.dp),
            ) {
                HsvColorPicker(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
                    controller = controller,
                    onColorChanged = { colorEnvelope: ColorEnvelope ->
                        state.setColor(colorEnvelope.color)
                    },
                )
                BrightnessSlider(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(36.dp),
                    controller = controller,
                )
                AlphaTile(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .clip(RoundedCornerShape(6.dp)),
                    controller = controller,
                )
            }
        },
        title = {
            Text(stringResource(id = R.string.settings_appearance_theme_color))
        },
    )
}

@Composable
private fun presenter(
    initialColor: ULong,
    settingsRepository: SettingsRepository = koinInject(),
    coroutineScope: CoroutineScope = koinInject(),
) = run {
    var selectedColor by remember { mutableStateOf(Color(initialColor)) }

    object {
        fun setColor(color: Color) {
            selectedColor = color
        }

        fun confirm() {
            coroutineScope.launch {
                settingsRepository.updateAppearance(AppearanceKeys.ColorSeed, selectedColor.value)
            }
        }
    }
}
