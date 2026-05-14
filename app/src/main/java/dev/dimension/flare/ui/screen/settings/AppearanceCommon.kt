package dev.dimension.flare.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.dimension.flare.data.model.appearance.AppearanceKey
import dev.dimension.flare.data.model.appearance.AppearancePatch
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.ui.component.FlareDropdownMenu
import dev.dimension.flare.ui.component.platform.isBigScreen
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.settings.AppearancePresenter
import dev.dimension.flare.ui.presenter.settings.AppearanceState
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun <T> SingleChoiceSettingsItem(
    headline: @Composable () -> Unit,
    supporting: @Composable () -> Unit,
    items: ImmutableMap<T, String>,
    selected: T,
    onSelected: (T) -> Unit,
    shapes: ListItemShapes,
    modifier: Modifier = Modifier,
) {
    val isBigScreen = isBigScreen()
    var showMenu by remember { mutableStateOf(false) }
    SegmentedListItem(
        modifier = modifier,
        checked = showMenu,
        onCheckedChange = {
            if (!isBigScreen) {
                showMenu = it
            }
        },
        shapes = shapes,
        content = headline,
        supportingContent = supporting,
        trailingContent = {
            if (isBigScreen) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                ) {
                    val entries = items.entries.toList()
                    entries.forEachIndexed { index, (value, label) ->
                        ToggleButton(
                            checked = selected == value,
                            onCheckedChange = { onSelected(value) },
                            shapes =
                                when (index) {
                                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                    entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                },
                        ) {
                            Text(text = label)
                        }
                    }
                }
            } else {
                TextButton(onClick = { showMenu = true }) {
                    Text(text = items[selected] ?: "")
                }
                FlareDropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                ) {
                    items.forEach { (value, label) ->
                        DropdownMenuItem(
                            text = { Text(text = label) },
                            onClick = {
                                onSelected(value)
                                showMenu = false
                            },
                        )
                    }
                }
            }
        },
    )
}

internal interface AppearanceSettingsUpdater : AppearanceState {
    val appearance: UiState<AppearancePatch>

    fun <T : Any> update(
        key: AppearanceKey<T>,
        value: T,
    )

    fun clear(key: AppearanceKey<*>)

    fun updateFontScale(fontSizeDiff: Float)
}

@Composable
internal fun appearancePresenter(): AppearanceSettingsUpdater =
    run {
        val scope = rememberCoroutineScope()
        val settingsRepository = koinInject<SettingsRepository>()
        val appearanceState = remember { AppearancePresenter() }.invoke()
        val appearance by settingsRepository.appearancePatch.collectAsUiState()

        object : AppearanceSettingsUpdater, AppearanceState by appearanceState {
            override val appearance: UiState<AppearancePatch> = appearance

            override fun <T : Any> update(
                key: AppearanceKey<T>,
                value: T,
            ) {
                scope.launch {
                    settingsRepository.updateAppearance(key, value)
                }
            }

            override fun clear(key: AppearanceKey<*>) {
                scope.launch {
                    settingsRepository.clearAppearance(key)
                }
            }

            override fun updateFontScale(fontSizeDiff: Float) {
                scope.launch {
                    settingsRepository.updateAppearance {
                        set(dev.dimension.flare.data.model.appearance.AppearanceKeys.FontSizeDiff, fontSizeDiff)
                            .set(dev.dimension.flare.data.model.appearance.AppearanceKeys.LineHeightDiff, fontSizeDiff * 2)
                    }
                }
            }
        }
    }
