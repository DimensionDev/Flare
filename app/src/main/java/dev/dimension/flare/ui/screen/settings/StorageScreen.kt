package dev.dimension.flare.ui.screen.settings

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import coil3.imageLoader
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Database
import compose.icons.fontawesomeicons.solid.Envelope
import compose.icons.fontawesomeicons.solid.FileExport
import compose.icons.fontawesomeicons.solid.FileImport
import compose.icons.fontawesomeicons.solid.Images
import dev.dimension.flare.R
import dev.dimension.flare.common.ComposeInAppNotification
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FlareLargeFlexibleTopAppBar
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.ThemeIconData
import dev.dimension.flare.ui.component.ThemedIcon
import dev.dimension.flare.ui.presenter.ExportDataPresenter
import dev.dimension.flare.ui.presenter.ImportDataPresenter
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.settings.StoragePresenter
import dev.dimension.flare.ui.presenter.settings.StorageState
import dev.dimension.flare.ui.theme.first
import dev.dimension.flare.ui.theme.item
import dev.dimension.flare.ui.theme.last
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.tlaster.precompose.molecule.producePresenter
import org.koin.compose.koinInject
import java.io.BufferedReader
import java.io.InputStreamReader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StorageScreen(
    onBack: () -> Unit,
    toAppLog: () -> Unit,
) {
    val topAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val context = LocalContext.current
    val state by producePresenter {
        storagePresenter(context = context)
    }
    FlareScaffold(
        topBar = {
            FlareLargeFlexibleTopAppBar(
                title = {
                    Text(text = stringResource(id = R.string.settings_storage_title))
                },
                navigationIcon = {
                    BackButton(onBack = onBack)
                },
                scrollBehavior = topAppBarScrollBehavior,
            )
        },
        modifier = Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
    ) {
        Column(
            modifier =
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(it)
                    .padding(horizontal = screenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            SegmentedListItem(
                onClick = {
                    state.clearImageCache()
                },
                shapes = ListItemDefaults.first(),
                content = {
                    Text(text = stringResource(id = R.string.settings_storage_clear_image_cache))
                },
                supportingContent = {
                    Text(
                        text =
                            stringResource(
                                id = R.string.settings_storage_clear_image_cache_description,
                                state.imageCacheSize,
                            ),
                    )
                },
                leadingContent = {
                    ThemedIcon(
                        FontAwesomeIcons.Solid.Images,
                        contentDescription = stringResource(id = R.string.settings_storage_clear_image_cache),
                        color = ThemeIconData.Color.ForestGreen,
                    )
                },
            )
            SegmentedListItem(
                onClick = {
                    state.clearCacheDatabase()
                },
                shapes = ListItemDefaults.item(),
                content = {
                    Text(text = stringResource(id = R.string.settings_storage_clear_database))
                },
                supportingContent = {
                    Text(
                        text =
                            stringResource(
                                id = R.string.settings_storage_clear_database_description,
                                state.userCount,
                                state.statusCount,
                            ),
                    )
                },
                leadingContent = {
                    ThemedIcon(
                        FontAwesomeIcons.Solid.Database,
                        contentDescription = stringResource(id = R.string.settings_storage_clear_database),
                        color = ThemeIconData.Color.ImperialMagenta,
                    )
                },
            )
            SegmentedListItem(
                onClick = {
                    toAppLog.invoke()
                },
                shapes = ListItemDefaults.item(),
                content = {
                    Text(text = stringResource(id = R.string.settings_storage_app_log))
                },
                supportingContent = {
                    Text(
                        text =
                            stringResource(id = R.string.settings_storage_app_log_description),
                    )
                },
                leadingContent = {
                    ThemedIcon(
                        FontAwesomeIcons.Solid.Envelope,
                        contentDescription = stringResource(id = R.string.settings_storage_app_log),
                        color = ThemeIconData.Color.DeepTeal,
                    )
                },
            )

            val exportLauncher =
                rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("application/json"),
                    onResult = { uri ->
                        uri?.let { state.export(it) }
                    },
                )

            SegmentedListItem(
                onClick = {
                    exportLauncher.launch("flare_data_export.json")
                },
                shapes = ListItemDefaults.item(),
                content = {
                    Text(text = stringResource(id = R.string.settings_storage_export_data))
                },
                supportingContent = {
                    Text(
                        text =
                            stringResource(id = R.string.settings_storage_export_data_description),
                    )
                },
                leadingContent = {
                    ThemedIcon(
                        FontAwesomeIcons.Solid.FileExport,
                        contentDescription = stringResource(id = R.string.settings_storage_export_data),
                        color = ThemeIconData.Color.DarkAmber,
                    )
                },
            )

            val importLauncher =
                rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument(),
                    onResult = { uri ->
                        uri?.let { state.import(it) }
                    },
                )

            SegmentedListItem(
                onClick = {
                    importLauncher.launch(arrayOf("application/json"))
                },
                shapes = ListItemDefaults.last(),
                content = {
                    Text(text = stringResource(id = R.string.settings_storage_import_data))
                },
                supportingContent = {
                    Text(
                        text =
                            stringResource(id = R.string.settings_storage_import_data_description),
                    )
                },
                leadingContent = {
                    ThemedIcon(
                        FontAwesomeIcons.Solid.FileImport,
                        contentDescription = stringResource(id = R.string.settings_storage_import_data),
                        color = ThemeIconData.Color.SapphireBlue,
                    )
                },
            )
        }
    }
}

@Composable
private fun storagePresenter(context: Context) =
    run {
        val state = remember { StoragePresenter() }.invoke()

        val notification = koinInject<ComposeInAppNotification>()
        val exportPresenter = remember { ExportDataPresenter() }
        val exportState = exportPresenter.body()
        val scope = rememberCoroutineScope()

        var importJson by remember { mutableStateOf<String?>(null) }
        val importPresenter = remember(importJson) { importJson?.let { ImportDataPresenter(it) } }
        val importState = importPresenter?.body()

        LaunchedEffect(importState) {
            importState?.let {
                try {
                    it.import()
                    notification.message(R.string.import_completed)
                } catch (e: Exception) {
                    notification.message(R.string.import_error)
                } finally {
                    importJson = null
                }
            }
        }

        var imageCacheSize by remember {
            mutableLongStateOf(
                context.imageLoader.diskCache
                    ?.size
                    ?.div(1024L * 1024L) ?: 0L,
            )
        }

        object : StorageState by state {
            val imageCacheSize: Long = imageCacheSize

            fun clearImageCache() {
                context.imageLoader.diskCache?.clear()
                imageCacheSize = 0L
            }

            fun clearCacheDatabase() {
                state.clearCache()
            }

            fun export(uri: android.net.Uri) {
                scope.launch {
                    val json = exportState.export()
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                            outputStream.write(json.toByteArray())
                        }
                    }
                    notification.message(R.string.save_completed)
                }
            }

            fun import(uri: android.net.Uri) {
                scope.launch {
                    val json =
                        withContext(Dispatchers.IO) {
                            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                                    reader.readText()
                                }
                            }
                        }
                    importJson = json
                }
            }
        }
    }
