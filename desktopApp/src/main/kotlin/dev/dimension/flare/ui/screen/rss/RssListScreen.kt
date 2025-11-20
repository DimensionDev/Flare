package dev.dimension.flare.ui.screen.rss

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.FileExport
import compose.icons.fontawesomeicons.solid.Plus
import dev.dimension.flare.LocalWindowPadding
import dev.dimension.flare.Res
import dev.dimension.flare.add_rss_source
import dev.dimension.flare.opml_export
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.model.UiRssSource
import dev.dimension.flare.ui.presenter.home.rss.ExportOPMLPresenter
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.theme.LocalComposeWindow
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import io.github.composefluent.component.AccentButton
import io.github.composefluent.component.ProgressBar
import io.github.composefluent.component.Text
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter
import org.jetbrains.compose.resources.stringResource
import java.awt.FileDialog
import java.io.File

@Composable
internal fun RssListScreen(
    toItem: (UiRssSource) -> Unit,
    onEdit: (Int) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val window = LocalComposeWindow.current
    val state by producePresenter {
        presenter(
            onFilePicker = {
                FileDialog(window, "Save", FileDialog.SAVE)
                    .apply {
                        file = "flare_export.opml"
                        isVisible = true
                    }.let {
                        if (it.directory != null && it.file != null) {
                            File(it.directory, it.file)
                        } else {
                            null
                        }
                    }
            },
        )
    }

    LazyColumn(
        modifier =
            modifier
                .padding(horizontal = screenHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        contentPadding = LocalWindowPadding.current,
    ) {
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                modifier = Modifier.fillParentMaxWidth(),
            ) {
                if (state.sources.any()) {
                    AccentButton(
                        onClick = {
                            state.export()
                        },
                    ) {
                        FAIcon(
                            FontAwesomeIcons.Solid.FileExport,
                            contentDescription = stringResource(Res.string.opml_export),
                        )
                        Text(
                            stringResource(Res.string.opml_export),
                        )
                    }
                }
                AccentButton(
                    onClick = {
                        onAdd.invoke()
                    },
                ) {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.Plus,
                        contentDescription = stringResource(Res.string.add_rss_source),
                    )
                    Text(
                        text = stringResource(Res.string.add_rss_source),
                    )
                }
            }
        }
        item {
            Spacer(modifier = Modifier.height(6.dp))
        }

        rssListWithTabs(
            state = state,
            onClicked = toItem,
            onEdit = onEdit,
        )
    }

    if (state.exporting) {
        ProgressBar(modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun presenter(onFilePicker: () -> File?) =
    run {
        val exportPresenter = remember { ExportOPMLPresenter() }
        val scope = rememberCoroutineScope()
        var exporting by remember { mutableStateOf(false) }
        val state = remember { RssListWithTabsPresenter() }.invoke()
        object : RssListWithTabsPresenter.State by state {
            val exporting = exporting

            fun export() {
                exporting = true
                scope.launch {
                    runCatching {
                        exportPresenter.export()
                    }.getOrNull().let {
                        if (it != null) {
                            onFilePicker()
                                ?.apply {
                                    createNewFile()
                                }?.writeText(it)
                        }
                    }
                    exporting = false
                }
            }
        }
    }
