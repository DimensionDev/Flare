package dev.dimension.flare.ui.screen.rss

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.FileExport
import compose.icons.fontawesomeicons.solid.Plus
import dev.dimension.flare.R
import dev.dimension.flare.common.ComposeInAppNotification
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareLargeFlexibleTopAppBar
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.model.UiRssSource
import dev.dimension.flare.ui.presenter.home.rss.ExportOPMLPresenter
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RssSourcesScreen(
    onAdd: () -> Unit,
    onEdit: (Int) -> Unit,
    onClicked: (UiRssSource) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val topAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val state by producePresenter {
        presenter(context)
    }
    val filePicker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("text/xml"),
        ) {
            if (it != null) {
                state.export(it)
            }
        }
    FlareScaffold(
        topBar = {
            FlareLargeFlexibleTopAppBar(
                title = {
                    Text(text = stringResource(R.string.rss_sources_title))
                },
                actions = {
                    if (state.sources.any()) {
                        IconButton(
                            onClick = {
                                filePicker.launch("flare_export.opml")
                            },
                        ) {
                            FAIcon(
                                FontAwesomeIcons.Solid.FileExport,
                                contentDescription = stringResource(R.string.opml_export),
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            onAdd.invoke()
                        },
                    ) {
                        FAIcon(
                            FontAwesomeIcons.Solid.Plus,
                            contentDescription = stringResource(R.string.add_rss_source),
                        )
                    }
                },
                navigationIcon = {
                    BackButton(onBack = onBack)
                },
                scrollBehavior = topAppBarScrollBehavior,
            )
        },
        modifier =
            Modifier
                .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
    ) { contentPadding ->
        LazyColumn(
            modifier =
                Modifier
                    .padding(horizontal = screenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            contentPadding = contentPadding,
        ) {
            rssListWithTabs(
                state = state,
                onClicked = onClicked,
                onEdit = onEdit,
            )
        }
    }
}

@Composable
private fun presenter(context: Context) =
    run {
        val inAppNotification = koinInject<ComposeInAppNotification>()
        val exportPresenter = remember { ExportOPMLPresenter() }
        val scope = rememberCoroutineScope()
        var exporting by remember { mutableStateOf(false) }
        val state = remember { RssListWithTabsPresenter() }.invoke()
        object : RssListWithTabsPresenter.State by state {
            val exporting = exporting

            fun export(uri: Uri) {
                exporting = true
                scope.launch {
                    runCatching {
                        exportPresenter.export()
                    }.getOrNull().let {
                        if (it != null) {
                            context.contentResolver.openOutputStream(uri)?.use {
                                it.write(it.toString().toByteArray())
                            }
                        }
                    }
                    exporting = false
                    inAppNotification.message(R.string.export_completed)
                }
            }
        }
    }
