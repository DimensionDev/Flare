package dev.dimension.flare.ui.presenter.compose

import dev.dimension.flare.data.datasource.microblog.ComposeData
import dev.dimension.flare.ui.model.UiTimelineV2

public interface ReferenceShareImageRenderer {
    public fun render(
        post: UiTimelineV2,
        completion: (media: ComposeData.Media?, errorMessage: String?) -> Unit,
    )
}
