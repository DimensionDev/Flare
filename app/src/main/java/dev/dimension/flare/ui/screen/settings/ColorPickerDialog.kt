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
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.spec.DestinationStyle
import dev.dimension.flare.R
import dev.dimension.flare.data.model.LocalAppearanceSettings
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.component.ThemeWrapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Destination<RootGraph>(
    wrappers = [ThemeWrapper::class],
    style = DestinationStyle.Dialog::class,
)
@Composable
internal fun ColorPickerDialogRoute(navigator: ProxyDestinationsNavigator) {
    ColorPickerDialog(
        onBack = navigator::navigateUp,
    )
}

@Composable
private fun ColorPickerDialog(onBack: () -> Unit) {
    val appearanceSettings = LocalAppearanceSettings.current
    val state by producePresenter {
        presenter(initialColor = appearanceSettings.colorSeed)
    }
    val controller = rememberColorPickerController()
    LaunchedEffect(Unit) {
        controller.selectByColor(Color(appearanceSettings.colorSeed), fromUser = true)
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
                settingsRepository.updateAppearanceSettings {
                    copy(colorSeed = selectedColor.value)
                }
            }
        }
    }
}
