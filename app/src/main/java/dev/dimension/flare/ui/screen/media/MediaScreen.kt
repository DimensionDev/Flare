package dev.dimension.flare.ui.screen.media

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import dev.dimension.flare.common.MediaFileNamePolicy
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableMap

@Composable
internal fun MediaScreen(
    uri: String,
    previewUrl: String?,
    customHeaders: Map<String, String>?,
    onDismiss: () -> Unit,
    toAltText: (UiMedia) -> Unit = {},
) {
    RawMediaScreen(
        medias =
            persistentListOf(
                UiMedia.Image(
                    url = uri,
                    previewUrl = previewUrl ?: uri,
                    description = null,
                    height = 0f,
                    width = 0f,
                    sensitive = false,
                    customHeaders = customHeaders?.toImmutableMap(),
                ),
            ),
        index = 0,
        preview = previewUrl,
        onDismiss = onDismiss,
        toAltText = toAltText,
        uriHandler = LocalUriHandler.current,
    )
}

@Composable
internal fun RawMediaScreen(
    medias: ImmutableList<UiMedia>,
    index: Int,
    preview: String?,
    onDismiss: () -> Unit,
    toAltText: (UiMedia) -> Unit,
    uriHandler: UriHandler,
) {
    MediaViewerScreen(
        medias = UiState.Success(medias),
        initialIndex = index.coerceIn(0, (medias.size - 1).coerceAtLeast(0)),
        preview = preview,
        onDismiss = onDismiss,
        toAltText = toAltText,
        uriHandler = uriHandler,
        fileName = MediaFileNamePolicy::rawMediaFileName,
        fileNames = MediaFileNamePolicy::rawMediaFileNames,
    )
}
