package dev.dimension.flare.ui.component

import androidx.compose.runtime.Composable
import dev.dimension.flare.compose.ui.Res
import dev.dimension.flare.compose.ui.media_audio_no_description
import dev.dimension.flare.compose.ui.media_image_no_alt
import dev.dimension.flare.compose.ui.media_video_no_alt
import dev.dimension.flare.ui.model.UiMedia
import org.jetbrains.compose.resources.stringResource

@Composable
public fun UiMedia.accessibleDescription(): String =
    description?.takeIf { it.isNotBlank() }
        ?: when (this) {
            is UiMedia.Image,
            is UiMedia.Gif,
            -> stringResource(Res.string.media_image_no_alt)

            is UiMedia.Video -> stringResource(Res.string.media_video_no_alt)

            is UiMedia.Audio -> stringResource(Res.string.media_audio_no_description)
        }
