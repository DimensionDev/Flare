package dev.dimension.flare.ui.screen.settings

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import coil3.imageLoader
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.AppLoggingRouteDestination
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Database
import compose.icons.fontawesomeicons.solid.Envelope
import compose.icons.fontawesomeicons.solid.Images
import dev.dimension.flare.R
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.FlareTopAppBar
import dev.dimension.flare.ui.component.ThemeWrapper
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.settings.StoragePresenter
import dev.dimension.flare.ui.presenter.settings.StorageState
import moe.tlaster.precompose.molecule.producePresenter

@Destination<RootGraph>(
    wrappers = [ThemeWrapper::class],
)
@Composable
internal fun StorageRoute(navigator: ProxyDestinationsNavigator) {
    StorageScreen(
        onBack = navigator::navigateUp,
        toAppLog = {
            navigator.navigate(AppLoggingRouteDestination)
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StorageScreen(
    onBack: () -> Unit,
    toAppLog: () -> Unit,
) {
    val context = LocalContext.current
    val state by producePresenter {
        storagePresenter(context = context)
    }
    FlareScaffold(
        topBar = {
            FlareTopAppBar(
                title = {
                    Text(text = stringResource(id = R.string.settings_storage_title))
                },
                navigationIcon = {
                    BackButton(onBack = onBack)
                },
            )
        },
    ) {
        Column(
            modifier =
                Modifier
                    .padding(it)
                    .verticalScroll(rememberScrollState()),
        ) {
            ListItem(
                headlineContent = {
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
                modifier =
                    Modifier.clickable {
                        state.clearImageCache()
                    },
                leadingContent = {
                    FAIcon(
                        FontAwesomeIcons.Solid.Images,
                        contentDescription = null,
                    )
                },
            )
            ListItem(
                headlineContent = {
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
                modifier =
                    Modifier.clickable {
                        state.clearCacheDatabase()
                    },
                leadingContent = {
                    FAIcon(
                        FontAwesomeIcons.Solid.Database,
                        contentDescription = null,
                    )
                },
            )
            ListItem(
                headlineContent = {
                    Text(text = stringResource(id = R.string.settings_storage_app_log))
                },
                supportingContent = {
                    Text(
                        text =
                            stringResource(id = R.string.settings_storage_app_log_description),
                    )
                },
                modifier =
                    Modifier.clickable {
                        toAppLog.invoke()
                    },
                leadingContent = {
                    FAIcon(
                        FontAwesomeIcons.Solid.Envelope,
                        contentDescription = null,
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
        }
    }
