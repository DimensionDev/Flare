package dev.dimension.flare.ui.screen.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dimension.flare.R
import dev.dimension.flare.data.model.appearance.AppearancePatch
import dev.dimension.flare.data.model.appearance.TimelineAppearance
import dev.dimension.flare.data.model.appearance.withPatch
import dev.dimension.flare.data.model.tab.TimelineFilterConfig
import dev.dimension.flare.data.model.tab.TimelineTabItemV2
import dev.dimension.flare.data.model.tab.isSystemHomeMixedTimeline
import dev.dimension.flare.ui.component.LocalTimelineAppearance
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.invoke
import moe.tlaster.precompose.molecule.producePresenter

@Composable
internal fun EditTabDialog(
    tabItem: TimelineTabItemV2,
    onDismissRequest: () -> Unit,
    onConfirm: (TimelineTabItemV2) -> Unit,
    titleAndIconOnly: Boolean = false,
) {
    val appearance = LocalTimelineAppearance.current
    val state by producePresenter(key = "EditTabSheet_$tabItem") {
        presenter(tabItem = tabItem, appearance = appearance)
    }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                enabled = state.canConfirm,
                onClick = {
                    onConfirm(
                        tabItem.withPresentationOverrides(
                            title = state.text.text.toString(),
                            icon = state.icon,
                            appearancePatch = state.appearancePatch,
                            enabled = state.enabled,
                            filterConfig = state.filterConfig,
                        ),
                    )
                },
            ) {
                Text(text = stringResource(id = android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(id = android.R.string.cancel))
            }
        },
        text = {
            TimelinePresentationEditor(
                text = state.text,
                icon = state.icon,
                availableIcons = state.availableIcons,
                showIconPicker = state.showIconPicker,
                onShowIconPickerChange = state::setShowIconPicker,
                withAvatar = state.withAvatar,
                canUseAvatar = !titleAndIconOnly && state.canUseAvatar,
                onWithAvatarChange = state::setWithAvatar,
                enabled = state.enabled,
                onEnabledChange = state::setEnabled,
                filterConfig = state.filterConfig,
                onFilterConfigChange = state::setFilterConfig,
                timelineAppearance = state.timelineAppearance,
                appearancePatch = state.appearancePatch,
                onAppearancePatchChange = state::setAppearancePatch,
                onIconChange = state::setIcon,
                showEnabled = !titleAndIconOnly && !tabItem.isSystemHomeMixedTimeline,
                showAppearanceOverrides = !titleAndIconOnly,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 560.dp)
                        .verticalScroll(rememberScrollState()),
            )
        },
        title = {
            Text(text = stringResource(id = R.string.edit_tab_title))
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
    var filterConfig by remember(tabItem) { mutableStateOf(tabItem.filterConfig) }
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
        val filterConfig = filterConfig
        val appearancePatch = appearancePatch
        val timelineAppearance = timelineAppearance

        fun setShowIconPicker(value: Boolean) {
            showIconPicker = value
        }

        fun setEnabled(value: Boolean) {
            enabled = value
        }

        fun setFilterConfig(value: TimelineFilterConfig) {
            filterConfig = value
        }

        fun setAppearancePatch(value: AppearancePatch) {
            appearancePatch = value
        }
    }
}
