package dev.dimension.flare.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import dev.dimension.flare.data.model.PostActionStyle
import dev.dimension.flare.ui.component.platform.PlatformTextStyle
import dev.dimension.flare.ui.component.status.StatusActions
import dev.dimension.flare.ui.presenter.profile.ProfileState
import dev.dimension.flare.ui.theme.PlatformTheme

@Composable
public fun ProfileMenu(
    profileState: ProfileState,
    modifier: Modifier = Modifier,
) {
    if (!profileState.actions.isEmpty()) {
        CompositionLocalProvider(
            LocalComponentAppearance provides
                LocalComponentAppearance.current.copy(
                    postActionStyle = PostActionStyle.RightAligned,
                ),
            PlatformTextStyle provides PlatformTheme.typography.title,
        ) {
            StatusActions(profileState.actions, modifier = modifier)
        }
    }
}
