package dev.dimension.flare.data.datasource.microblog.loader

import dev.dimension.flare.ui.model.UiProfile

internal interface UserLoader {
    suspend fun userByHandleAndHost(
        handle: String,
        host: String,
    ): UiProfile

    suspend fun userById(id: String): UiProfile
}
