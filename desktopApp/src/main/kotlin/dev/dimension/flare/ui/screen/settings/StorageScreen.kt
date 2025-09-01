package dev.dimension.flare.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil3.PlatformContext
import coil3.SingletonImageLoader
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Database
import compose.icons.fontawesomeicons.solid.Images
import dev.dimension.flare.*
import dev.dimension.flare.LocalWindowPadding
import dev.dimension.flare.Res
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.settings.StoragePresenter
import dev.dimension.flare.ui.presenter.settings.StorageState
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import io.github.composefluent.component.CardExpanderItem
import io.github.composefluent.component.ScrollbarContainer
import io.github.composefluent.component.Text
import moe.tlaster.precompose.molecule.producePresenter
import org.jetbrains.compose.resources.stringResource

@Composable
fun StorageScreen() {
    val state by producePresenter {
        presenter()
    }
    val scrollState = rememberScrollState()

    ScrollbarContainer(
        rememberScrollbarAdapter(scrollState),
    ) {
        Column(
            modifier =
                Modifier
                    .verticalScroll(scrollState)
                    .padding(LocalWindowPadding.current)
                    .padding(horizontal = screenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            CardExpanderItem(
                onClick = {
                    state.clearImageCache()
                },
                heading = {
                    Text(text = stringResource(Res.string.settings_storage_clear_image_cache))
                },
                caption = {
                    Text(
                        text =
                            stringResource(
                                Res.string.settings_storage_clear_image_cache_description,
                                state.imageCacheSize,
                            ),
                    )
                },
                icon = {
                    FAIcon(
                        FontAwesomeIcons.Solid.Images,
                        contentDescription = stringResource(Res.string.settings_storage_clear_image_cache),
                    )
                },
            )
            CardExpanderItem(
                onClick = {
                    state.clearCacheDatabase()
                },
                heading = {
                    Text(text = stringResource(Res.string.settings_storage_clear_database))
                },
                caption = {
                    Text(
                        text =
                            stringResource(
                                Res.string.settings_storage_clear_database_description,
                                state.userCount,
                                state.statusCount,
                            ),
                    )
                },
                icon = {
                    FAIcon(
                        FontAwesomeIcons.Solid.Database,
                        contentDescription = stringResource(Res.string.settings_storage_clear_database),
                    )
                },
            )
        }
    }
}

@Composable
private fun presenter() =
    run {
        val state = remember { StoragePresenter() }.invoke()
        var imageCacheSize by remember {
            mutableLongStateOf(
                SingletonImageLoader
                    .get(PlatformContext.INSTANCE)
                    .diskCache
                    ?.size
                    ?.div(1024L * 1024L) ?: 0L,
            )
        }

        object : StorageState by state {
            val imageCacheSize: Long = imageCacheSize

            fun clearImageCache() {
                SingletonImageLoader.get(PlatformContext.INSTANCE).diskCache?.clear()
                imageCacheSize = 0L
            }

            fun clearCacheDatabase() {
                state.clearCache()
            }
        }
    }
