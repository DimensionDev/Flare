package dev.dimension.flare.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.FloppyDisk
import compose.icons.fontawesomeicons.solid.Trash
import dev.dimension.flare.LocalWindowPadding
import dev.dimension.flare.Res
import dev.dimension.flare.settings_app_logging_clear
import dev.dimension.flare.settings_app_logging_enable_network_logging
import dev.dimension.flare.settings_app_logging_save
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.settings.DevModePresenter
import dev.dimension.flare.ui.theme.LocalComposeWindow
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import io.github.composefluent.component.AccentButton
import io.github.composefluent.component.Expander
import io.github.composefluent.component.ExpanderItem
import io.github.composefluent.component.SubtleButton
import io.github.composefluent.component.Switcher
import io.github.composefluent.component.Text
import moe.tlaster.precompose.molecule.producePresenter
import org.jetbrains.compose.resources.stringResource
import java.awt.FileDialog
import java.io.File
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
@Composable
internal fun AppLoggingScreen() {
    val window = LocalComposeWindow.current
    val state by producePresenter { presenter() }
    LazyColumn(
        modifier =
            Modifier
                .padding(horizontal = screenHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        contentPadding = LocalWindowPadding.current,
    ) {
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                modifier = Modifier.fillParentMaxWidth(),
            ) {
                SubtleButton(
                    onClick = {
                        FileDialog(window).apply {
                            mode = FileDialog.SAVE
                            file = "flare-log-${Clock.System.now().toEpochMilliseconds()}.txt"
                            isVisible = true
                            val dir = directory
                            val file = file
                            if (dir != null && file != null) {
                                val data = state.printMessageToString()
                                val file = File(dir, file)
                                file.writeText(data)
                            }
                        }
                    },
                ) {
                    FAIcon(
                        FontAwesomeIcons.Solid.FloppyDisk,
                        contentDescription = stringResource(Res.string.settings_app_logging_save),
                    )
                    Text(
                        stringResource(Res.string.settings_app_logging_save),
                    )
                }
                AccentButton(
                    onClick = {
                        state.clear()
                    },
                ) {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.Trash,
                        contentDescription = stringResource(Res.string.settings_app_logging_clear),
                    )
                    Text(
                        text = stringResource(Res.string.settings_app_logging_clear),
                    )
                }
            }
        }
        item {
            Spacer(modifier = Modifier.height(6.dp))
        }
        item {
            ExpanderItem(
                heading = {
                    Text(stringResource(Res.string.settings_app_logging_enable_network_logging))
                },
                trailing = {
                    Switcher(
                        checked = state.enabled,
                        onCheckStateChange = {
                            state.setEnabled(it)
                        },
                        textBefore = true,
                    )
                },
            )
        }
        item {
            Spacer(modifier = Modifier.height(12.dp))
        }
        items(state.messages) { it ->
            var isExpanded by remember { mutableStateOf(false) }
            Expander(
                expanded = isExpanded,
                onExpandedChanged = {
                    isExpanded = it
                },
                heading = {
                    Text(it, maxLines = 3)
                },
                expandContent = {
                    Text(
                        it,
                        modifier = Modifier.padding(16.dp),
                    )
                },
            )
        }
    }
}

@Composable
private fun presenter() =
    run {
        remember { DevModePresenter() }.invoke()
    }
