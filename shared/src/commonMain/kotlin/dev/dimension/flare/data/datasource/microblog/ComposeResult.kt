package dev.dimension.flare.data.datasource.microblog

import androidx.compose.runtime.Immutable
import dev.dimension.flare.model.MicroBlogKey

@Immutable
public data class ComposeResult(
    val remotePostKey: MicroBlogKey? = null,
)
