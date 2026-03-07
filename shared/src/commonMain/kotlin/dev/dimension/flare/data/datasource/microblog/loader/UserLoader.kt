package dev.dimension.flare.data.datasource.microblog.loader

import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiProfile

internal interface UserLoader {
    suspend fun userByHandleAndHost(uiHandle: UiHandle): UiProfile

    suspend fun userById(id: String): UiProfile
}
