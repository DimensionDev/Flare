package dev.dimension.flare.model

import androidx.compose.runtime.Immutable
import dev.dimension.flare.data.datasource.microblog.ComposeType
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.collections.immutable.ImmutableList
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public data class ComposeInitialTextContext(
    public val post: UiTimelineV2.Post,
    public val quotes: ImmutableList<UiTimelineV2.Post>,
    public val composeType: ComposeType,
    public val currentUserHandle: UiHandle,
    public val selectedAccountKey: MicroBlogKey,
)

@Immutable
public data class InitialText(
    public val text: String,
    public val cursorPosition: Int,
)
