package dev.dimension.flare.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.dimension.flare.Res
import dev.dimension.flare.cancel
import dev.dimension.flare.data.model.appearance.AppearancePatch
import dev.dimension.flare.data.model.appearance.TimelineAppearance
import dev.dimension.flare.data.model.appearance.withPatch
import dev.dimension.flare.data.model.tab.TimelineTabItemV2
import dev.dimension.flare.data.model.tab.isSystemHomeMixedTimeline
import dev.dimension.flare.edit_tab_name
import dev.dimension.flare.edit_tab_name_placeholder
import dev.dimension.flare.edit_tab_title
import dev.dimension.flare.ok
import dev.dimension.flare.ui.component.LocalTimelineAppearance
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.screen.settings.EditTabPresenter
import io.github.composefluent.component.ContentDialog
import io.github.composefluent.component.ContentDialogButton
import io.github.composefluent.component.Text
import moe.tlaster.precompose.molecule.producePresenter
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun EditTabDialog(
    visible: Boolean,
    tabItem: TimelineTabItemV2,
    onDismissRequest: () -> Unit,
    onConfirm: (TimelineTabItemV2) -> Unit,
) {
    val appearance = LocalTimelineAppearance.current
    val state by producePresenter(key = "EditTabSheet_$tabItem") {
        presenter(tabItem = tabItem, appearance = appearance)
    }
    ContentDialog(
        visible = visible,
        title = stringResource(Res.string.edit_tab_title),
        primaryButtonText = stringResource(Res.string.ok),
        closeButtonText = stringResource(Res.string.cancel),
        onButtonClick = {
            when (it) {
                ContentDialogButton.Primary -> {
                    if (state.canConfirm) {
                        onConfirm(
                            tabItem.withPresentationOverrides(
                                title = state.text.text.toString(),
                                icon = state.icon,
                                appearancePatch = state.appearancePatch,
                                enabled = state.enabled,
                            ),
                        )
                    }
                }

                ContentDialogButton.Secondary -> {
                    Unit
                }

                ContentDialogButton.Close -> {
                    onDismissRequest()
                }
            }
        },
        content = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            ) {
                TimelinePresentationEditor(
                    text = state.text,
                    icon = state.icon,
                    availableIcons = state.availableIcons,
                    showIconPicker = state.showIconPicker,
                    onShowIconPickerChange = state::setShowIconPicker,
                    withAvatar = state.withAvatar,
                    canUseAvatar = state.canUseAvatar,
                    onWithAvatarChange = state::setWithAvatar,
                    enabled = state.enabled,
                    onEnabledChange = state::setEnabled,
                    timelineAppearance = state.timelineAppearance,
                    appearancePatch = state.appearancePatch,
                    onAppearancePatchChange = state::setAppearancePatch,
                    onIconChange = state::setIcon,
                    showEnabled = !tabItem.isSystemHomeMixedTimeline,
                    modifier = Modifier.fillMaxWidth(),
                    header = {
                        Text(text = stringResource(Res.string.edit_tab_name))
                    },
                    placeholder = {
                        Text(text = stringResource(Res.string.edit_tab_name_placeholder))
                    },
                )
            }
        },
    )
}

@Composable
private fun presenter(
    tabItem: TimelineTabItemV2,
    appearance: TimelineAppearance,
) = run {
    val text = rememberTextFieldState()
    val state =
        remember(tabItem) {
            EditTabPresenter(tabItem)
        }.invoke()
    var showIconPicker by remember { mutableStateOf(false) }
    var enabled by remember(tabItem) { mutableStateOf(tabItem.enabled) }
    var appearancePatch by remember(tabItem) { mutableStateOf(tabItem.appearancePatch ?: AppearancePatch.EMPTY) }
    val timelineAppearance by remember {
        derivedStateOf {
            appearance.withPatch(appearancePatch)
        }
    }
    state.initialText.onSuccess {
        LaunchedEffect(it) {
            text.edit {
                replace(0, length, it)
            }
        }
    }
    object : EditTabPresenter.State by state {
        val text = text
        val canConfirm = text.text.isNotEmpty()
        val showIconPicker = showIconPicker
        val enabled = enabled
        val appearancePatch = appearancePatch
        val timelineAppearance = timelineAppearance

        fun setShowIconPicker(value: Boolean) {
            showIconPicker = value
        }

        fun setEnabled(value: Boolean) {
            enabled = value
        }

        fun setAppearancePatch(value: AppearancePatch) {
            appearancePatch = value
        }
    }
}
